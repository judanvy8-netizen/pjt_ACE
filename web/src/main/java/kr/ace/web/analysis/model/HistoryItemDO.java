package kr.ace.web.analysis.model;

import kr.ace.web.analysis.entity.AnalysisRun;
import kr.ace.web.truth.entity.Evaluation;

public record HistoryItemDO(AnalysisRun run, Evaluation evaluation, boolean reviewed) {}
