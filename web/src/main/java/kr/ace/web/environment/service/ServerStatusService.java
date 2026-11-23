package kr.ace.web.environment.service;

import kr.ace.web.analysis.client.AiClient;
import kr.ace.web.environment.model.ServerStatusDO;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class ServerStatusService {
    private final AiClient ai;
    private final JdbcTemplate statusJdbc;

    public ServerStatusService(AiClient ai, javax.sql.DataSource dataSource) {
        this.ai = ai;
        this.statusJdbc = new JdbcTemplate(dataSource);
        this.statusJdbc.setQueryTimeout(3);
    }

    public ServerStatusDO python() {
        try {
            var health = ai.healthDetails();
            if (health == null) return new ServerStatusDO("warning", "AI 상태 응답을 확인해 주세요.");
            return Boolean.TRUE.equals(health.ok()) ? new ServerStatusDO("up", "AI 상태 API 응답을 확인했습니다.")
                    : new ServerStatusDO("warning", "API가 응답했습니다. 모델 파일과 AI 로그를 확인해 주세요.");
        } catch (RestClientException e) {
            return new ServerStatusDO("down", "AI 상태 API를 조회하지 못했습니다.");
        }
    }

    public ServerStatusDO database() {
        try {
            statusJdbc.queryForObject("SELECT 1", Integer.class);
            return new ServerStatusDO("up", "ACE의 MySQL 연결로 조회했습니다.");
        } catch (DataAccessException e) {
            return new ServerStatusDO("down", "ACE의 MySQL 연결로 조회하지 못했습니다.");
        }
    }
}
