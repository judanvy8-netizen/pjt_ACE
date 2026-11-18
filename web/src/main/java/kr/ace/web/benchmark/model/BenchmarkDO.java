package kr.ace.web.benchmark.model;

import java.util.List;
import java.util.Map;

public final class BenchmarkDO {
    private BenchmarkDO() {}

    public record Counts(int cats, int dogs) {
        public String display() {
            return cats + " / " + dogs;
        }
    }

    public record Truth(String filename, Counts counts, String breeds, String note) {}
    public record Version(String id, String label, String description) {}
    public record Summary(Version version, int correct, int total, int missing, int issues,
                          int improved, int regressed, Double catMae, Double dogMae, int worsened, int breedCorrect, int breedTotal, int unsupported, int breedSupportUnknown, Map<String,Integer> countDistribution) {}
    public record Result(Version version, Counts counts, String status, boolean image,
                         List<Map<String, String>> classes, boolean classificationWarning, kr.ace.web.evaluation.Verdict verdict) {}
    public record Photo(Truth truth, boolean image, List<Result> results) {}

    public record CurrentPhoto(String filename, Counts expected, Counts actual,
                               boolean matched, boolean nonTarget, boolean falsePositive) {}
    public record CurrentRun(String version, int correct, int total, int nonTargetCorrect,
                             int nonTargetFalsePositive, List<CurrentPhoto> photos) {}

    public record Rate(String version, long correct, long total, long missing) {
        public double percent() { return total == 0 ? 0 : 100.0 * correct / total; }
    }
    public record CountGroup(int expectedTotal, int photos, List<Rate> rates) {}
    public record PhotoStatus(String version, String status, String label) {}
    public record PhotoTile(int number, String filename, List<PhotoStatus> statuses) {}
    public record Performance(List<Rate> versions, List<CountGroup> groups, List<PhotoTile> photos) {}
}
