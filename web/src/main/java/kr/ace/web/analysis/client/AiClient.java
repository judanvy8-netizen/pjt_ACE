package kr.ace.web.analysis.client;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import kr.ace.web.analysis.model.InferenceDO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class AiClient {
    private final RestClient inference;
    private final RestClient health;

    public AiClient(@Value("${ace.ai-url}") String url) {
        inference = client(url, 120);
        health = client(url, 3);
    }

    private static RestClient client(String url, int seconds) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(seconds));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public boolean healthy() {
        try {
            // A reachable API can report missing models. Those failures belong to each inference.
            health.get().uri("/api/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Health(Boolean ok) {}

    public Health healthDetails() {
        return health.get().uri("/api/health").retrieve().body(Health.class);
    }

    public InferenceDO infer(String id, String path, String variant) {
        try {
            return inference.post().uri("/api/infer")
                    .body(Map.of("run_id", id, "relative_path", path, "version", variant))
                    .retrieve().body(InferenceDO.class);
        } catch (RestClientResponseException e) {
            String message = switch (e.getStatusCode().value()) {
                case 422 -> "사진을 읽을 수 없습니다.";
                case 503 -> "모델을 불러오지 못했습니다. Python 창을 확인해 주세요.";
                default -> e.getStatusCode().is4xxClientError()
                        ? "AI 서버가 사진 또는 요청을 거부했습니다." : "AI 서버에서 분석에 실패했습니다.";
            };
            throw new AiFailedException(message, e);
        } catch (ResourceAccessException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConnectException || cause instanceof java.net.UnknownHostException
                        || cause instanceof java.net.NoRouteToHostException
                        || cause instanceof java.io.EOFException) {
                    throw new AiUnavailableException(e);
                }
                if (cause instanceof java.net.SocketException && cause.getMessage() != null
                        && cause.getMessage().toLowerCase(java.util.Locale.ROOT).contains("reset")) {
                    throw new AiUnavailableException(e);
                }
            }
            throw new AiFailedException("AI 서버 응답이 늦거나 통신에 실패했습니다.", e);
        } catch (RestClientException e) {
            throw new AiFailedException("AI 응답 형식이 올바르지 않습니다.", e);
        }
    }

    public static class AiFailedException extends RuntimeException {
        public AiFailedException(String message, Throwable cause) { super(message, cause); }
    }

    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(Throwable cause) {
            super("AI 서버에 연결할 수 없습니다. Python 창을 확인해 주세요.", cause);
        }
    }
}
