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
    public record Version(String id, String label, String description, String models) {}
    /** Count agreement only; no ground-truth boxes exist for object-level precision. */
    public record SpeciesCount(int matched, int expected, int predicted) {
        public int shortage() { return expected - matched; }
        public int excess() { return predicted - matched; }
    }
    public record CountMismatch(String filename, Counts expected, Counts predicted) {
        public boolean bothWrong() {
            return expected.cats() != predicted.cats() && expected.dogs() != predicted.dogs();
        }
    }
    public record Summary(Version version, int correct, int total, int missing, int issues,
                          int improved, int regressed, Double catMae, Double dogMae, int worsened,
                          SpeciesCount cats, SpeciesCount dogs,
                          int breedCorrect, int breedTotal, int unsupported, int breedSupportUnknown,
                          Map<String,Integer> countDistribution, List<CountMismatch> mismatches) {
        public double photoPercent() { return total == 0 ? 0 : 100.0 * correct / total; }
        public double breedPercent() { return breedTotal == 0 ? 0 : 100.0 * breedCorrect / breedTotal; }
        public int bothWrongPhotos() {
            return (int) mismatches.stream().filter(CountMismatch::bothWrong).count();
        }
        public int oneWrongPhotos() { return mismatches.size() - bothWrongPhotos(); }
        public int countErrors() {
            return cats.shortage() + cats.excess() + dogs.shortage() + dogs.excess();
        }
    }
    public record Result(Version version, Counts counts, String status, boolean image,
                         List<Map<String, String>> classes, boolean classificationWarning, kr.ace.web.evaluation.Verdict verdict) {
        public Double breedConfidence(Map<String, String> row) {
            String value = row.get("class_confidence");
            if (value == null || value.isBlank()) return null;
            try {
                double score = Double.parseDouble(value);
                return score >= 0 && score <= 1 ? score : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
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
