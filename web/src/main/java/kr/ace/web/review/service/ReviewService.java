package kr.ace.web.review.service;

import java.util.ArrayList;
import kr.ace.web.analysis.repository.AnalysisRepository;
import kr.ace.web.analysis.service.AnalysisService;
import kr.ace.web.evaluation.GroundTruthParser;
import kr.ace.web.truth.entity.GroundTruth;
import kr.ace.web.truth.entity.GroundTruthPet;
import kr.ace.web.truth.repository.TruthRepository;
import kr.ace.web.truth.service.ReferenceTruthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final TruthRepository truth;
    private final AnalysisRepository runs;
    private final AnalysisService analysis;
    private final ReferenceTruthService reference;

    public ReviewService(TruthRepository truth, AnalysisRepository runs, AnalysisService analysis,
                         ReferenceTruthService reference) {
        this.truth = truth;
        this.runs = runs;
        this.analysis = analysis;
        this.reference = reference;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void save(String runId) {
        var run = analysis.require(runId);
        if (!"SUCCEEDED".equals(run.status())) {
            throw new IllegalArgumentException("성공한 결과에서 검수해 주세요.");
        }
        var candidate = reference.find(run.filename(), run.sourcePath());
        if (candidate.truth() == null) throw new IllegalArgumentException("등록된 CSV 정답이 없습니다.");
        var counts = candidate.truth().counts();
        var pets = new ArrayList<GroundTruthPet>();
        for (var pet : GroundTruthParser.parse(candidate.truth().breeds(), counts.cats(), counts.dogs())) {
            pets.add(new GroundTruthPet(pets.size() + 1, pet.species(), pet.breed(), pet.position()));
        }
        truth.lockInput(run.inputId());
        var latest = truth.latest(run.inputId());
        if (latest.isPresent() && latest.get().cats() == counts.cats() && latest.get().dogs() == counts.dogs()
                && pets.equals(truth.pets(run.inputId(), latest.get().version()))) return;
        var expected = new GroundTruth(run.inputId(), truth.nextVersion(run.inputId()), counts.cats(), counts.dogs());
        truth.save(expected);
        truth.savePets(expected, pets);
        for (var item : runs.forInput(run.inputId())) {
            if ("SUCCEEDED".equals(item.status())) analysis.evaluate(item, expected, pets);
        }
    }
}
