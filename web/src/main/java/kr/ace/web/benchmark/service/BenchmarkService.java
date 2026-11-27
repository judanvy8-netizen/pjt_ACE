package kr.ace.web.benchmark.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kr.ace.web.benchmark.model.BenchmarkDO.*;
import kr.ace.web.benchmark.repository.BenchmarkRepository;
import kr.ace.web.benchmark.repository.CsvTable;
import org.springframework.stereotype.Service;
import kr.ace.web.evaluation.BreedRules;
import kr.ace.web.evaluation.GroundTruthParser;
import kr.ace.web.evaluation.Verdict;

@Service
public class BenchmarkService {
    private static final List<Version> VERSIONS = List.of(
            new Version("main", "개·고양이 검출",
                    "YOLO로 위치를 찾고 종류별 마릿수를 셉니다.",
                    "검출 YOLO11n"),
            new Version("main-1", "품종 후보 분류",
                    "main의 검출 결과를 유지하면서 각 동물의 품종 후보를 붙입니다.",
                    "검출 YOLO11n · 분류 YOLO11n-cls (ImageNet)"),
            new Version("main-2", "품종 분류와 검출 보정",
                    "중복 검출을 줄이고 넓은 박스를 다시 확인합니다. 고양이 전용 품종 분류기를 추가합니다.",
                    "검출 YOLO11n · 견종 YOLO11n-cls · 묘종 MobileNetV3-Large (Oxford)"),
            new Version("main-3", "ViT 품종 분류와 종 보정",
                    "YOLO 검출 뒤 ViT-B/16으로 견종·종을 확인하고 Oxford 모델로 묘종을 분류합니다.",
                    "검출 YOLO11n · 종 보정·견종 ViT-B/16 · 묘종 MobileNetV3-Large (Oxford)"));
    private final BenchmarkRepository files;

    private final BreedRules rules;

    public BenchmarkService(BenchmarkRepository files, BreedRules rules) {
        this.files = files;
        this.rules = rules;
    }

    public Map<String, Truth> truth() { return truth("data/ground_truth.csv"); }

    public Map<String, Truth> referenceTruth() {
        var result = truth();
        truth("data/holdout/ground_truth.csv").forEach(result::putIfAbsent);
        return result;
    }

    public Optional<Path> referenceImage(String name) {
        return files.image(truth().containsKey(name) ? "original" : "holdout", name);
    }

    private Map<String, Truth> truth(String path) {
        var result = new LinkedHashMap<String, Truth>();
        var seen = new HashSet<String>();
        for (var row : files.csv(path).rows()) {
            String name = row.get("파일명");
            if (name == null || !name.matches("[A-Za-z0-9_-]+\\.(?i:jpg|jpeg|png|bmp|webp)")) {
                continue;
            }
            if (!seen.add(name)) {
                result.remove(name);
                continue;
            }
            var counts = counts(row, "고양이_정답", "개_정답");
            if (counts != null) {
                result.put(name, new Truth(name, counts, row.getOrDefault("종류", ""), row.getOrDefault("비고", "")));
            }
        }
        return result;
    }

    public Performance performance() {
        var baseline = truth().values().stream().sorted(java.util.Comparator.comparing(Truth::filename)).toList();
        var tables = VERSIONS.stream().collect(java.util.stream.Collectors.toMap(Version::id, this::predictions));
        var tiles = new ArrayList<PhotoTile>();
        var groups = new java.util.TreeMap<Integer, List<PhotoTile>>();
        for (Truth expected : baseline) {
            var statuses = VERSIONS.stream().map(version -> {
                var table = tables.get(version.id());
                Counts actual = table.values().get(expected.filename());
                if (table.flagged().contains(expected.filename())) {
                    return new PhotoStatus(version.id(), "missing", "자료 확인 필요");
                }
                if (actual == null) return new PhotoStatus(version.id(), "missing", "자료 없음");
                return actual.equals(expected.counts()) ? new PhotoStatus(version.id(), "matched", "마릿수 일치")
                        : new PhotoStatus(version.id(), "mismatched", "마릿수 불일치");
            }).toList();
            var tile = new PhotoTile(tiles.size() + 1, expected.filename(), statuses);
            tiles.add(tile);
            int total = expected.counts().cats() + expected.counts().dogs();
            groups.computeIfAbsent(total, ignored -> new ArrayList<>()).add(tile);
        }
        var byCount = groups.entrySet().stream().map(group -> new CountGroup(group.getKey(),
                group.getValue().size(), performanceRates(group.getValue()))).toList();
        return new Performance(performanceRates(tiles), byCount, tiles);
    }

    private List<Rate> performanceRates(List<PhotoTile> tiles) {
        return VERSIONS.stream().map(version -> {
            var statuses = tiles.stream().flatMap(tile -> tile.statuses().stream())
                    .filter(status -> version.id().equals(status.version())).toList();
            long correct = statuses.stream().filter(status -> "matched".equals(status.status())).count();
            long missing = statuses.stream().filter(status -> "missing".equals(status.status())).count();
            return new Rate(version.id(), correct, tiles.size(), missing);
        }).toList();
    }

    /** Validate that each displayed run covers the current 40-photo truth set exactly once. */
    public Map<String, CurrentRun> currentRuns() {
        var expected = truth();
        var result = new LinkedHashMap<String, CurrentRun>();
        for (var version : VERSIONS) {
            var csv = files.resultCsv(version.id(), "pet_counts.csv");
            if (csv.invalidRows() != 0 || !csv.headers().containsAll(List.of("filename", "cat", "dog"))) continue;
            var predicted = new LinkedHashMap<String, Counts>();
            boolean invalid = false;
            for (var row : csv.rows()) {
                String filename = row.get("filename");
                Counts counts = counts(row, "cat", "dog");
                if (filename == null || !expected.containsKey(filename) || counts == null
                        || predicted.putIfAbsent(filename, counts) != null) {
                    invalid = true;
                    break;
                }
            }
            if (invalid || predicted.size() != expected.size()) continue;
            var photos = new ArrayList<CurrentPhoto>();
            int correct = 0, nonTargetCorrect = 0, nonTargetFalsePositive = 0;
            for (var truth : expected.values()) {
                Counts actual = predicted.get(truth.filename());
                boolean matched = truth.counts().equals(actual);
                boolean nonTarget = truth.counts().cats() == 0 && truth.counts().dogs() == 0;
                boolean falsePositive = nonTarget && !matched;
                if (matched) correct++;
                if (nonTarget && matched) nonTargetCorrect++;
                if (falsePositive) nonTargetFalsePositive++;
                photos.add(new CurrentPhoto(truth.filename(), truth.counts(), actual,
                        matched, nonTarget, falsePositive));
            }
            result.put(version.id(), new CurrentRun(version.id(), correct, photos.size(),
                    nonTargetCorrect, nonTargetFalsePositive, photos));
        }
        return result;
    }

    public List<Summary> summaries() {
        var truth = truth();
        int expectedCats = truth.values().stream().mapToInt(item -> item.counts().cats()).sum();
        int expectedDogs = truth.values().stream().mapToInt(item -> item.counts().dogs()).sum();
        var previous = Map.<String, Counts>of();
        var summaries = new ArrayList<Summary>();
        for (Version version : VERSIONS) {
            var table = predictions(version);
            int correct = 0, missing = 0, improved = 0, regressed = 0, catError = 0, dogError = 0, worsened = 0;
            int matchedCats = 0, matchedDogs = 0, predictedCats = 0, predictedDogs = 0;
            int breedCorrect = 0, breedTotal = 0, unsupported = 0, unknown = 0;
            var distribution = new LinkedHashMap<String,Integer>();
            var mismatches = new ArrayList<CountMismatch>();
            var classifications = classTable(version);
            for (var item : truth.values()) {
                Counts current = table.values().get(item.filename());
                Counts prior = previous.get(item.filename());
                if (current == null) {
                    missing++;
                    continue;
                }
                int dc = current.cats() - item.counts().cats();
                int dd = current.dogs() - item.counts().dogs();
                matchedCats += Math.min(current.cats(), item.counts().cats());
                matchedDogs += Math.min(current.dogs(), item.counts().dogs());
                predictedCats += current.cats();
                predictedDogs += current.dogs();
                catError += Math.abs(dc);
                dogError += Math.abs(dd);
                distribution.merge(Verdict.countLabel(item.counts().cats(), item.counts().dogs(), dc, dd), 1, Integer::sum);
                if (prior != null && Math.abs(dc) + Math.abs(dd)
                        > Math.abs(prior.cats() - item.counts().cats()) + Math.abs(prior.dogs() - item.counts().dogs())) worsened++;
                var classes = classification(version, item.filename(), classifications);
                var verdict = classify(version, item, current, classes);
                if (verdict != null) {
                    breedCorrect += verdict.correct();
                    breedTotal += verdict.total();
                    unsupported += verdict.unsupported();
                    unknown += verdict.supportUnknown();
                }
                boolean matched = current.equals(item.counts());
                if (matched) {
                    correct++;
                } else {
                    mismatches.add(new CountMismatch(item.filename(), item.counts(), current));
                }
                if (prior != null && matched != prior.equals(item.counts())) {
                    if (matched) improved++;
                    else regressed++;
                }
            }
            int measured = truth.size() - missing;
            summaries.add(new Summary(version, correct, truth.size(), missing, table.issues(), improved, regressed,
                    measured == 0 ? null : (double) catError / measured,
                    measured == 0 ? null : (double) dogError / measured, worsened,
                    new SpeciesCount(matchedCats, expectedCats, predictedCats),
                    new SpeciesCount(matchedDogs, expectedDogs, predictedDogs),
                    breedCorrect, breedTotal, unsupported,
                    unknown, distribution, List.copyOf(mismatches)));
            previous = table.values();
        }
        return summaries;
    }

    public Optional<Photo> photo(String name) {
        Truth expected = truth().get(name);
        if (expected == null) {
            return Optional.empty();
        }
        var results = new ArrayList<Result>();
        for (Version version : VERSIONS) {
            var table = predictions(version);
            Counts prediction = table.values().get(name);
            String status = table.flagged().contains(name) ? "확인 필요"
                    : prediction == null ? "자료 없음" : Verdict.countLabel(expected.counts().cats(), expected.counts().dogs(),
                            prediction.cats()-expected.counts().cats(), prediction.dogs()-expected.counts().dogs());
            var classes = classification(version, name, classTable(version));
            results.add(new Result(version, prediction, status, files.image(version.id(), name).isPresent(),
                    classes.rows(), classes.warning(), classify(version, expected, prediction, classes)));
        }
        return Optional.of(new Photo(expected, files.image("original", name).isPresent(), results));
    }

    public Optional<Path> image(String version, String name) {
        if (!("original".equals(version) || VERSIONS.stream().anyMatch(v -> v.id().equals(version)))
                || !truth().containsKey(name)) {
            return Optional.empty();
        }
        return files.image(version, name);
    }

    private record Classification(List<Map<String, String>> rows, boolean warning) {}

    private CsvTable classTable(Version version) {
        return "main".equals(version.id()) ? new CsvTable(List.of(), List.of(), 0)
                : files.resultCsv(version.id(), "pet_classifications.csv");
    }

    private Verdict classify(Version version, Truth expected, Counts result, Classification classes) {
        if (result == null || classes.warning() || "main".equals(version.id())) return null;
        try {
            var pets = GroundTruthParser.parse(expected.breeds(), expected.counts().cats(), expected.counts().dogs())
                    .stream().map(p -> new Verdict.Pet(p.species(), p.breed())).toList();
            return Verdict.compare(expected.counts().cats(), expected.counts().dogs(), result.cats(), result.dogs(),
                    pets, classes.rows().stream().filter(row -> "yes".equals(row.get("species_match")))
                            .map(row -> new Verdict.Pet(row.get("detected_species"), row.get("class_label"))).toList(),
                    rules.vocabulary(version.id()), rules::normalize);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Classification classification(Version version, String filename, CsvTable csv) {
        if ("main".equals(version.id())) return new Classification(List.of(), false);
        var rows = csv.rows().stream().filter(row -> filename.equals(row.get("filename"))).toList();
        var seen = new HashSet<String>();
        boolean duplicate = rows.stream().anyMatch(row -> !seen.add(row.get("object_no")));
        return new Classification(rows, duplicate || csv.invalidRows() > 0 || csv.headers().isEmpty());
    }

    private record Predictions(Map<String, Counts> values, Set<String> flagged, int issues) {}

    private Predictions predictions(Version version) {
        var csv = files.resultCsv(version.id(), "pet_counts.csv");
        var values = new LinkedHashMap<String, Counts>();
        var seen = new HashSet<String>();
        var flagged = new HashSet<String>();
        for (var row : csv.rows()) {
            String name = row.get("filename");
            var parsed = counts(row, "cat", "dog");
            if (name == null || name.isBlank() || !seen.add(name) || parsed == null) {
                flagged.add(name);
                values.remove(name);
            } else {
                values.put(name, parsed);
            }
        }
        return new Predictions(values, flagged, csv.invalidRows() + flagged.size());
    }

    private Counts counts(Map<String, String> row, String cats, String dogs) {
        try {
            int catCount = Integer.parseInt(row.getOrDefault(cats, "").strip());
            int dogCount = Integer.parseInt(row.getOrDefault(dogs, "").strip());
            return catCount < 0 || dogCount < 0 ? null : new Counts(catCount, dogCount);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
