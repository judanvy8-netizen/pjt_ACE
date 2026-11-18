package kr.ace.web.dataset.model;

import java.util.List;
import kr.ace.web.analysis.entity.AnalysisRun;
import kr.ace.web.analysis.entity.DetectedPet;
import kr.ace.web.truth.entity.Evaluation;

public final class RoundDO {
    private RoundDO() {}

    public record Item(AnalysisRun run, String variant, Evaluation evaluation, List<DetectedPet> pets) {}
    public record Photo(long id, String filename, List<Item> items) {}
    public record Progress(int total, int success, int failed, int dismissed, boolean running) {}
    public record Score(String variant, long correct, long truth, long pendingReview, long failed, long waiting, Double catMae, Double dogMae, long breedCorrect, long breedTotal,
                        long unsupported, java.util.Map<String,Long> distribution, long worsened, boolean unknownSupport) {
        public String supportScore() {
            if (breedTotal == 0) return "평가 제외";
            if (unknownSupport) return "지원 기준: 견종 매핑 확인 필요";
            if (breedTotal == unsupported) return "지원 품종 없음";
            return "지원 기준 " + breedCorrect + "/" + (breedTotal - unsupported);
        }
    }
}
