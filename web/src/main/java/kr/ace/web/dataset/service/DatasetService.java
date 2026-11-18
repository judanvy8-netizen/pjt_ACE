package kr.ace.web.dataset.service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import kr.ace.web.analysis.entity.AnalysisRun;
import kr.ace.web.analysis.client.AiClient;
import kr.ace.web.analysis.repository.AnalysisRepository;
import kr.ace.web.analysis.service.AnalysisService;
import kr.ace.web.dataset.entity.AnalysisRound;
import kr.ace.web.dataset.entity.Dataset;
import kr.ace.web.dataset.model.RoundDO.*;
import kr.ace.web.dataset.repository.DatasetRepository;
import kr.ace.web.media.entity.InputMedia;
import kr.ace.web.media.model.UploadDO;
import kr.ace.web.media.model.PhotoNavigationDO.Choice;
import kr.ace.web.evaluation.GroundTruthParser;
import kr.ace.web.evaluation.Verdict;
import kr.ace.web.benchmark.model.BenchmarkDO.Truth;
import kr.ace.web.media.repository.MediaRepository;
import kr.ace.web.media.service.MediaService;
import kr.ace.web.truth.repository.TruthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DatasetService {
    private static final Logger LOG = LoggerFactory.getLogger(DatasetService.class);
    public static final List<String> VARIANTS = List.of("main", "main-1", "main-2");
    private final DatasetRepository datasets;
    private final AiClient ai;
    private final kr.ace.web.evaluation.BreedRules breedRules;
    private final AnalysisRepository runs;
    private final AnalysisService analysis;
    private final MediaRepository inputs;
    private final MediaService storage;
    private final TruthRepository truth;
    private final TransactionTemplate tx;
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<String> active = ConcurrentHashMap.newKeySet();

    public DatasetService(DatasetRepository datasets, AnalysisRepository runs, AnalysisService analysis,
            MediaRepository inputs, MediaService storage, TruthRepository truth, TransactionTemplate tx, AiClient ai, kr.ace.web.evaluation.BreedRules breedRules) {
        this.datasets = datasets;
        this.runs = runs;
        this.analysis = analysis;
        this.inputs = inputs;
        this.storage = storage;
        this.truth = truth;
        this.tx = tx;
        this.ai = ai;
        this.breedRules = breedRules;
    }

    @PreDestroy
    public void stop() {
        worker.shutdownNow();
    }

    public String register(String name, MultipartFile[] photos) throws IOException {
        if (photos.length < 1 || photos.length > 40) {
            throw new IllegalArgumentException("사진을 1~40장 선택해 주세요.");
        }
        String label = name.strip();
        if (label.isEmpty() || label.length() > 120) {
            throw new IllegalArgumentException("데이터셋 이름을 1~120자로 입력해 주세요.");
        }
        String id = UUID.randomUUID().toString();
        var stored = new ArrayList<InputMedia>();
        try {
            for (var photo : photos) {
                stored.add(storage.save(new UploadDO(photo), id));
            }
            tx.executeWithoutResult(status -> {
                datasets.create(id, label);
                stored.forEach(input -> inputs.insert(input, id));
            });
        } catch (IOException | RuntimeException e) {
            for (var input : stored) {
                try {
                    Files.deleteIfExists(storage.file(input.relativePath()).getFile().toPath());
                } catch (IOException cleanup) {
                    e.addSuppressed(cleanup);
                }
            }
            throw e;
        }
        return id;
    }

    public List<Dataset> datasets() { return datasets.datasets(); }
    public List<String> filenames(String id) { return datasets.filenames(id); }
    public List<AnalysisRound> rounds(String id) { return datasets.rounds(id); }

    private void ensureAi() {
        if (!ai.healthy()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "AI 서버에 연결할 수 없습니다. Python 창을 확인해 주세요.");
    }

    public Dataset dataset(String id) {
        return datasets.dataset(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public AnalysisRound round(String id) {
        return datasets.round(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public String start(String datasetId, String token) {
        dataset(datasetId);
        ensureAi();
        try {
            if (!UUID.fromString(token).toString().equals(token)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 분석 요청입니다.");
        }
        tx.executeWithoutResult(status -> {
            datasets.lock(datasetId);
            var existing = datasets.round(token);
            if (existing.isPresent()) {
                if (!datasetId.equals(existing.get().datasetId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT);
                }
                return;
            }
            datasets.createRound(token, datasetId);
            for (long inputId : datasets.inputIds(datasetId)) {
                for (String variant : VARIANTS) {
                    runs.create(UUID.randomUUID().toString(), inputId, token, variant);
                }
            }
        });
        schedule(token, run -> "PENDING".equals(run.status()));
        return token;
    }

    public void resume(String roundId) {
        round(roundId);
        ensureAi();
        schedule(roundId, run -> "PENDING".equals(run.status()));
    }

    public void retry(String roundId, String runId) {
        round(roundId);
        if (!roundId.equals(datasets.roundForRun(runId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ensureAi();
        if (!schedule(roundId, run -> runId.equals(run.runId()) && "FAILED".equals(run.status()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "현재 회차의 처리가 끝난 뒤 다시 시도해 주세요.");
        }
    }

    public void retryFailed(String roundId) {
        round(roundId);
        ensureAi();
        if (!schedule(roundId, run -> "FAILED".equals(run.status()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "현재 회차 처리가 끝난 뒤 다시 시도해 주세요.");
        }
    }

    private boolean schedule(String roundId, Predicate<AnalysisRun> selected) {
        if (!active.add(roundId)) {
            return false;
        }
        worker.submit(() -> {
            try {
                int unavailable = 0;
                for (var run : runs.round(roundId)) {
                    if (Thread.currentThread().isInterrupted()) break;
                    if (!selected.test(run)) continue;
                    try {
                        analysis.analyze(run.runId());
                        unavailable = 0;
                    } catch (AiClient.AiUnavailableException e) {
                        if (++unavailable >= 3) break;
                    } catch (RuntimeException e) {
                        unavailable = 0;
                        LOG.error("분석 저장 실패: {}", run.runId(), e);
                        try {
                            runs.failure(run.runId(), "분석 또는 DB 저장 실패");
                        } catch (RuntimeException failure) {
                            LOG.error("실패 상태 저장 오류: {}", run.runId(), failure);
                        }
                    }
                }
            } finally {
                active.remove(roundId);
            }
        });
        return true;
    }

    public Progress progress(String id) {
        var counts = runs.statusCounts(id);
        return new Progress(counts.values().stream().mapToInt(Integer::intValue).sum(),
                counts.getOrDefault("SUCCEEDED", 0), counts.getOrDefault("FAILED", 0),
                counts.getOrDefault("DISMISSED", 0), active.contains(id));
    }

    public List<Photo> photos(String id, Long inputId) {
        var selected = runs.round(id).stream().filter(run -> inputId == null || inputId == run.inputId()).toList();
        var evaluations = truth.latestByRuns(selected.stream().map(AnalysisRun::runId).toList());
        var groups = selected.stream()
                .collect(Collectors.groupingBy(AnalysisRun::inputId, java.util.LinkedHashMap::new, Collectors.toList()));
        return groups.entrySet().stream().map(group -> new Photo(group.getKey(), group.getValue().get(0).filename(),
                group.getValue().stream().map(run -> new Item(run, run.variant(), evaluations.get(run.runId()),
                        inputId == null ? List.of() : runs.pets(run.runId()))).toList())).toList();
    }

    public List<Choice> photoChoices(String roundId) {
        return runs.round(roundId).stream()
                .sorted(java.util.Comparator.comparing(AnalysisRun::filename).thenComparingLong(AnalysisRun::inputId))
                .map(run -> new Choice(Long.toString(run.inputId()), run.filename())).distinct().toList();
    }

    public boolean reviewed(long inputId) {
        return truth.latest(inputId).isPresent();
    }

    // 비교 화면의 CSV 미리보기와 DB 평가에 같은 판정식을 사용합니다.
    public Map<String, Verdict> previewVerdicts(Photo photo, Truth reference) {
        var counts = reference.counts();
        var pets = GroundTruthParser.parse(reference.breeds(), counts.cats(), counts.dogs()).stream()
                .map(p -> new Verdict.Pet(p.species(), p.breed())).toList();
        var verdicts = new java.util.HashMap<String, Verdict>();
        for (var item : photo.items()) {
            if ("SUCCEEDED".equals(item.run().status())) {
                verdicts.put(item.variant(), analysis.verdict(item.run(), counts.cats(), counts.dogs(), pets, item.pets()));
            }
        }
        return verdicts;
    }

    public List<Score> scores(List<Photo> photos) {
        long truthCount = photos.stream().filter(photo -> photo.items().stream()
                .anyMatch(item -> item.evaluation() != null)).count();
        return VARIANTS.stream().map(variant -> {
            var items = photos.stream().flatMap(photo -> photo.items().stream()).filter(i -> variant.equals(i.variant())).toList();
            var evaluated = items.stream().map(Item::evaluation).filter(java.util.Objects::nonNull).toList();
            var distribution = evaluated.stream().collect(Collectors.groupingBy(
                    kr.ace.web.truth.entity.Evaluation::countLabel, java.util.LinkedHashMap::new, Collectors.counting()));
            int position = VARIANTS.indexOf(variant);
            long worsened = position == 0 ? 0 : photos.stream().filter(photo -> {
                var previous = photo.items().get(position - 1).evaluation();
                var current = photo.items().get(position).evaluation();
                return previous != null && current != null
                        && current.catError() + current.dogError() > previous.catError() + previous.dogError();
            }).count();
            return new Score(variant, items.stream().filter(i -> i.evaluation() != null && i.evaluation().matched()).count(),
                    truthCount, items.stream().filter(i -> i.evaluation() == null && "SUCCEEDED".equals(i.run().status())).count(),
                    items.stream().filter(i -> "FAILED".equals(i.run().status())).count(),
                    items.stream().filter(i -> "PENDING".equals(i.run().status())).count(),
                    evaluated.isEmpty() ? null : evaluated.stream().mapToInt(e -> e.catError()).average().orElseThrow(),
                    evaluated.isEmpty() ? null : evaluated.stream().mapToInt(e -> e.dogError()).average().orElseThrow(),
                    evaluated.stream().mapToLong(e -> e.breedCorrect()).sum(),
                    evaluated.stream().mapToLong(e -> e.breedTotal()).sum(),
                    evaluated.stream().mapToLong(e -> e.breedUnsupported()).sum(), distribution, worsened,
                    !breedRules.vocabulary(variant).containsKey("dog") && evaluated.stream().anyMatch(e -> e.dogs() > 0));
        }).toList();
    }
}
