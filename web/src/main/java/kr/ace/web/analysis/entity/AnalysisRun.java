package kr.ace.web.analysis.entity;

import java.time.LocalDateTime;

public record AnalysisRun(String runId, long inputId, String filename, String sourcePath,
        String status, String error, Integer catCount, Integer dogCount, String resultPath,
        String engine, Long elapsedMs, Integer width, Integer height, LocalDateTime createdAt, String variant, String roundId) {}
