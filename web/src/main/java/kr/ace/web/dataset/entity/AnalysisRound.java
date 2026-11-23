package kr.ace.web.dataset.entity;

import java.time.LocalDateTime;

public record AnalysisRound(String id, String datasetId, String datasetName, LocalDateTime createdAt) {}
