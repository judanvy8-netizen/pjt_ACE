package kr.ace.web.evaluation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.ace.web.benchmark.repository.BenchmarkRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class BreedAliasLookupController {
    private final BenchmarkRepository repository;
    private final BreedRules rules;
    public BreedAliasLookupController(BenchmarkRepository repository, BreedRules rules) {
        this.repository = repository;
        this.rules = rules;
    }
    public record Entry(String standard, String matchedName, List<String> aliases, String normalizedKey) { }

    @GetMapping("/api/breed-aliases")
    @ResponseBody
    public List<Entry> lookup(@RequestParam(defaultValue = "") String q) {
        String query = q.strip();
        if (query.isBlank() || query.length() > 100) return List.of();
        String needle = query.toLowerCase(Locale.ROOT);
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (String[] source : new String[][] {
            {"data/breed_aliases.csv", "alias"}, {"data/breed_labels_imagenet_ko.csv", "label"}}) {
            for (var row : repository.csv(source[0]).rows()) {
                String alias = row.getOrDefault(source[1], "");
                String standard = row.getOrDefault("canonical", "");
                if (alias.isBlank() || standard.isBlank()) continue;
                String key = rules.normalize(standard);
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(alias);
                display.putIfAbsent(key, standard);
            }
        }
        List<Entry> found = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            String standard = display.get(entry.getKey());
            List<String> candidates = entry.getValue().stream().distinct().sorted().toList();
            boolean matches = standard.toLowerCase(Locale.ROOT).contains(needle)
                    || candidates.stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(needle));
            if (matches) {
                String matched = candidates.stream().filter(a -> a.toLowerCase(Locale.ROOT).contains(needle))
                        .findFirst().orElse(standard);
                found.add(new Entry(standard, matched, candidates, entry.getKey()));
            }
        }
        found.sort(Comparator.comparing(Entry::standard));
        return found.stream().limit(50).toList();
    }
}
