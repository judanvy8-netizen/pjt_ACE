package kr.ace.web.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class GroundTruthParser {
    private static final Pattern PET = Pattern.compile("(고양이|개)\\d*\\((.+)\\)");

    private GroundTruthParser() {}

    public record Entry(String species, String breed, String position) {}

    public static List<Entry> parse(String text, int cats, int dogs) {
        if ("해당 없음".equals(text.strip()) && cats == 0 && dogs == 0) return List.of();
        var result = new ArrayList<Entry>();
        for (String token : text.split(";", -1)) {
            var matcher = PET.matcher(token.strip());
            if (!matcher.matches()) throw new IllegalArgumentException("CSV의 종류 열을 확인해 주세요.");
            String value = matcher.group(2);
            int separator = value.lastIndexOf(':');
            String breed = value.substring(separator + 1).strip();
            if (breed.isEmpty()) throw new IllegalArgumentException("CSV에 품종명이 비어 있습니다.");
            result.add(new Entry(matcher.group(1).equals("개") ? "dog" : "cat", breed,
                    separator < 0 ? "" : value.substring(0, separator).strip()));
        }
        if (result.size() != cats + dogs || result.stream().filter(p -> p.species().equals("cat")).count() != cats) {
            throw new IllegalArgumentException("CSV의 마릿수와 종류 열을 확인해 주세요.");
        }
        return List.copyOf(result);
    }
}
