package kr.ace.web.evaluation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public record Verdict(int catDiff, int dogDiff, int correct, int total, int unsupported, int predicted, int supportUnknown) {
    public record Pet(String species, String breed) {}

    public static Verdict compare(int cats, int dogs, int predictedCats, int predictedDogs,
            List<Pet> expected, List<Pet> actual, Map<String, java.util.Set<String>> vocabulary,
            UnaryOperator<String> normalize) {
        var counts = new HashMap<Pet, Integer>();
        for (Pet pet : actual) {
            counts.merge(new Pet(pet.species(), normalize.apply(pet.breed())), 1, Integer::sum);
        }
        int correct = 0;
        int unsupported = 0;
        int unknown = 0;
        for (Pet pet : expected) {
            String breed = normalize.apply(pet.breed());
            var known = vocabulary.get(pet.species());
            if (known != null && !known.contains(breed)) {
                unsupported++;
                continue;
            }
            if (known == null) unknown++;
            var key = new Pet(pet.species(), breed);
            int remaining = counts.getOrDefault(key, 0);
            if (remaining > 0) {
                correct++;
                counts.put(key, remaining - 1);
            }
        }
        return new Verdict(predictedCats - cats, predictedDogs - dogs, correct, expected.size(),
                unsupported, predictedCats + predictedDogs, unknown);
    }

    public static String countLabel(int cats, int dogs, int dc, int dd) {
        if (dc == 0 && dd == 0) return "일치";
        if (cats == 0 && dogs == 0) return "다른 동물 오검출";
        if ((long) dc * dd < 0) return "종 혼동 의심";
        return dc < 0 || dd < 0 ? "개수 부족" : "개수 초과";
    }

    public String countLabel(int cats, int dogs) {
        return countLabel(cats, dogs, catDiff, dogDiff);
    }

    public String breedLabel() {
        if (total == 0) return "평가 제외";
        String label = correct == total && predicted == total ? "전체 일치"
                : correct == 0 || correct == total ? "불일치" : "일부 일치";
        return label + " " + correct + "/" + total + (unsupported > 0 ? " · 미지원 " + unsupported : "")
                + (supportUnknown > 0 ? " · 지원 정보 확인 필요 " + supportUnknown : "");
    }
}
