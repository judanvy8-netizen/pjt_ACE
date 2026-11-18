package kr.ace.web.analysis.service;

import java.io.IOException;
import kr.ace.web.analysis.client.AiClient;
import kr.ace.web.evaluation.BreedRules;
import kr.ace.web.evaluation.Verdict;
import kr.ace.web.truth.entity.GroundTruth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ace.web.analysis.entity.AnalysisRun;
import kr.ace.web.analysis.entity.DetectedPet;
import kr.ace.web.analysis.model.AnalysisDetailDO;
import kr.ace.web.analysis.model.HistoryItemDO;
import kr.ace.web.analysis.model.HistorySummaryDO;
import kr.ace.web.truth.entity.GroundTruthPet;
import kr.ace.web.truth.repository.TruthRepository;
import kr.ace.web.analysis.model.InferenceDO;
import kr.ace.web.analysis.repository.AnalysisRepository;
import kr.ace.web.media.repository.MediaRepository;
import kr.ace.web.media.service.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import kr.ace.web.media.model.UploadDO;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnalysisService {
    private final AnalysisRepository runs;
    private final MediaRepository inputs;
    private final TruthRepository truth;
    private final MediaService storage;
    private final TransactionTemplate transactions;
    private final AiClient ai;
    private final BreedRules breeds;
    private final ObjectMapper json;

    public AnalysisService(AnalysisRepository runs, MediaRepository inputs, MediaService storage, TruthRepository truth,
            TransactionTemplate transactions, ObjectMapper json, AiClient ai, BreedRules breeds) {
        this.runs = runs;
        this.inputs = inputs;
        this.truth = truth;
        this.storage = storage;
        this.transactions = transactions;
        this.json = json;
        this.ai = ai;
        this.breeds = breeds;
    }

    public String upload(UploadDO upload) throws IOException {
        var input = storage.save(upload);
        try {
            transactions.executeWithoutResult(tx -> runs.create(input.runId(), inputs.insert(input)));
        } catch (org.springframework.dao.DataAccessException e) {
            try {
                java.nio.file.Files.deleteIfExists(storage.file(input.relativePath()).getFile().toPath());
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
        try {
            analyze(input.runId());
        } catch (AiClient.AiUnavailableException e) {
            runs.failure(input.runId(), e.getMessage());
        } catch (org.springframework.dao.DataAccessException e) {
            throw new ResultSaveException(input.runId(), e);
        }
        return input.runId();
    }

    public static class ResultSaveException extends RuntimeException {
        private final String runId;

        public ResultSaveException(String runId, Throwable cause) {
            super(cause);
            this.runId = runId;
        }

        public String runId() {
            return runId;
        }
    }

    public AnalysisRun require(String runId) {
        try {
            if (!UUID.fromString(runId).toString().equals(runId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return runs.find(runId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<DetectedPet> objects(String runId) {
        return runs.pets(runId);
    }

    public List<HistoryItemDO> history(String filter) {
        var list = runs.list(filter);
        var latest = truth.latestByRuns(list.stream().map(AnalysisRun::runId).toList());
        var reviewed = truth.reviewedInputs(list.stream().map(AnalysisRun::inputId).distinct().toList());
        return list.stream().map(run -> new HistoryItemDO(run, latest.get(run.runId()), reviewed.contains(run.inputId())))
                .toList();
    }

    public HistorySummaryDO historySummary() {
        return runs.historySummary();
    }

    public int dismissFailed(List<String> submitted) {
        if (submitted == null || submitted.isEmpty() || submitted.size() > 200) {
            throw new IllegalArgumentException("삭제할 실패 기록을 1~200건 선택해 주세요.");
        }
        var ids = new java.util.LinkedHashSet<String>();
        for (String id : submitted) {
            if (id == null || !UUID.fromString(id).toString().equals(id)) {
                throw new IllegalArgumentException("올바른 실행을 선택해 주세요.");
            }
            ids.add(id);
        }
        return runs.dismissFailed(ids);
    }

    public AnalysisDetailDO detail(String runId) {
        var run = require(runId);
        var versions = truth.history(runId);
        var latest = versions.isEmpty() ? null : versions.get(0);
        var expected = latest == null ? List.<GroundTruthPet>of() : truth.pets(run.inputId(), latest.version());
        return new AnalysisDetailDO(run, runs.pets(runId), versions, latest, expected);
    }

    public void evaluate(AnalysisRun run, GroundTruth expected, List<GroundTruthPet> pets) {
        var expectedPets = pets.stream().map(p -> new Verdict.Pet(p.species(), p.breed())).toList();
        truth.evaluate(run.runId(), expected,
                verdict(run, expected.cats(), expected.dogs(), expectedPets, runs.pets(run.runId())));
    }

    public Verdict verdict(AnalysisRun run, int cats, int dogs, List<Verdict.Pet> expected,
                           List<DetectedPet> detected) {
        boolean countOnly = "main".equals(run.variant());
        return Verdict.compare(cats, dogs, run.catCount(), run.dogCount(), countOnly ? List.of() : expected,
                countOnly ? List.of() : detected.stream().filter(p -> "yes".equals(p.classificationStatus()))
                        .map(p -> new Verdict.Pet(p.species(), p.breed())).toList(),
                breeds.vocabulary(run.variant()), breeds::normalize);
    }

    public void analyze(String runId) {
        var run = require(runId);
        if ("SUCCEEDED".equals(run.status()) || "DISMISSED".equals(run.status())) {
            return;
        }
        String variant = run.variant();
        InferenceDO result;
        try {
            result = ai.infer(runId, run.sourcePath(), variant);
        } catch (AiClient.AiFailedException e) {
            runs.failure(runId, e.getMessage());
            return;
        }
        if (result == null || !runId.equals(result.runId()) || result.objects() == null
                || !("ace-" + variant).equals(result.engineVersion())
                || !("results/" + runId + "/marked.jpg").equals(result.resultPath())) {
            runs.failure(runId, "AI 응답 형식이 올바르지 않습니다.");
            return;
        }
        try {
            storage.file(result.resultPath());
        } catch (IOException e) {
            runs.failure(runId, "Spring과 Python이 같은 공유 폴더를 사용하는지 확인해 주세요.");
            return;
        }
        String metadata = json.writeValueAsString(Map.of("models", result.models(), "settings", result.settings()));
        transactions.executeWithoutResult(tx -> {
            truth.lockInput(run.inputId());
            runs.lock(runId);
            String status = require(runId).status();
            if ("PENDING".equals(status) || "FAILED".equals(status)) {
                runs.success(result, metadata);
                truth.latest(run.inputId()).ifPresent(expected ->
                        evaluate(require(runId), expected, truth.pets(run.inputId(), expected.version())));
            }
        });
    }
}
