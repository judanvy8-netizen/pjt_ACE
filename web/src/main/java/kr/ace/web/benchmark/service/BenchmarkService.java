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
            new Version("main", "개·고양이 검출", "YOLO로 위치를 찾고 종류별 마릿수를 셉니다."),
            new Version("main-1", "품종 후보 분류", "검출 영역을 잘라 ImageNet 분류기에 전달합니다."),
            new Version("main-2", "품종 분류와 검출 보정", "고양이 전용 모델, 중복 제거, 넓은 박스 재검출을 적용합니다."));
    private final BenchmarkRepository files;

    private final BreedRules rules;

    public BenchmarkService(BenchmarkRepository files, BreedRules rules) {
        this.files = files;
        this.rules = rules;
    }

    public boolean standardBaseline() { return files.standardBaseline(); }

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

    public List<Summary> summaries() {
        var truth = truth();
        var previous = Map.<String, Counts>of();
        var summaries = new ArrayList<Summary>();
        for (Version version : VERSIONS) {
            var table = predictions(version);
            int correct = 0, missing = 0, improved = 0, regressed = 0, catError = 0, dogError = 0, worsened = 0;
            int breedCorrect = 0, breedTotal = 0, unsupported = 0, unknown = 0;
            var distribution = new LinkedHashMap<String,Integer>();
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
                }
                if (prior != null && matched != prior.equals(item.counts())) {
                    if (matched) improved++;
                    else regressed++;
                }
            }
            int measured = truth.size() - missing;
            summaries.add(new Summary(version, correct, truth.size(), missing, table.issues(), improved, regressed,
                    measured == 0 ? null : (double) catError / measured,
                    measured == 0 ? null : (double) dogError / measured, worsened, breedCorrect, breedTotal, unsupported,
                    unknown, distribution));
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

    public CsvTable verification(boolean classification) {
        return files.csv(classification ? "test_cases_classification.csv" : "test_cases.csv");
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
