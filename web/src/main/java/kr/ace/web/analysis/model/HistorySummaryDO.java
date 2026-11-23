package kr.ace.web.analysis.model;

public record HistorySummaryDO(long total, long evaluated, long matched, Double catMae, Double dogMae) {
    public long mismatched() {
        return evaluated - matched;
    }

    public Double accuracy() {
        return evaluated == 0 ? null : 100.0 * matched / evaluated;
    }
}
