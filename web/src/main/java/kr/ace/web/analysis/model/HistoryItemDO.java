package kr.ace.web.analysis.model;

import kr.ace.web.analysis.entity.AnalysisRun;
import kr.ace.web.analysis.entity.DetectedPet;
import kr.ace.web.truth.entity.Evaluation;
import java.util.List;

public record HistoryItemDO(AnalysisRun run, Evaluation evaluation, boolean reviewed, List<DetectedPet> pets) {}
