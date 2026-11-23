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
}
