package kr.ace.web.analysis.model;

import java.util.List;
import kr.ace.web.analysis.entity.AnalysisRun;
import kr.ace.web.analysis.entity.DetectedPet;
import kr.ace.web.truth.entity.Evaluation;
import kr.ace.web.truth.entity.GroundTruthPet;

public record AnalysisDetailDO(AnalysisRun run, List<DetectedPet> objects, List<Evaluation> evaluations,
        Evaluation latest, List<GroundTruthPet> expected) {}
