package kr.ace.web.evaluation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kr.ace.web.benchmark.repository.BenchmarkRepository;
import org.springframework.stereotype.Service;

@Service
public class BreedRules {
    private final Map<String, String> translations;
    private final Map<String, String> aliases;
    private final Map<String, Set<String>> dogLabels = new HashMap<>();

    public BreedRules(BenchmarkRepository repository) {
        translations = mapping(repository, "data/breed_labels_imagenet_ko.csv", "label");
        aliases = mapping(repository, "data/breed_aliases.csv", "alias");
        for (var row : repository.csv("data/vit_dog_labels.csv").rows()) {
            dogLabels.computeIfAbsent("main-3", ignored -> new java.util.HashSet<>())
                    .add(key(row.get("label")));
        }
        for (var row : repository.csv("data/breed_vocabulary.csv").rows()) {
            if ("dog".equals(row.get("species")) && row.get("label") != null) {
                dogLabels.computeIfAbsent(row.get("version"), ignored -> new java.util.HashSet<>())
                        .add(key(row.get("label")));
            }
        }
    }

    private Map<String, String> mapping(BenchmarkRepository repository, String path, String from) {
        var result = new HashMap<String, String>();
        for (var row : repository.csv(path).rows()) {
            result.put(key(row.get(from)), key(row.get("canonical")));
        }
        return result;
    }

    private String key(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "");
    }

    public String normalize(String value) {
        String normalized = key(value);
        normalized = translations.getOrDefault(normalized, normalized);
        return aliases.getOrDefault(normalized, normalized);
    }

    public Map<String, Set<String>> vocabulary(String variant) {
        // main-1의 검출 종 검증 범위 281~285와 main-2의 CAT_CLASS_NAMES.
        Stream<String> cats = switch (variant) {
            case "main-1" -> Stream.of("tabby", "tiger_cat", "Persian_cat", "Siamese_cat", "Egyptian_cat");
            case "main-2", "main-3" -> Stream.of("Abyssinian", "Bengal", "Birman", "Bombay", "British Shorthair",
                    "Egyptian Mau", "Maine Coon", "Persian", "Ragdoll", "Russian Blue", "Siamese", "Sphynx");
            default -> Stream.empty();
        };
        var result = new HashMap<String, Set<String>>();
        result.put("cat", cats.map(this::normalize).collect(Collectors.toUnmodifiableSet()));
        var dogs = dogLabels.getOrDefault(variant, Set.of());
        if (dogs.size() == 118 && dogs.stream().allMatch(translations::containsKey)) {
            result.put("dog", dogs.stream().map(this::normalize).collect(Collectors.toUnmodifiableSet()));
        }
        return result;
    }
}
