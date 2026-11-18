package kr.ace.web.evaluation;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerdictTest {
    @Test void countsDistinguishMixedErrorAndForeignAnimal() {
        assertEquals("종 혼동 의심", Verdict.countLabel(0, 2, 1, -1));
        assertEquals("다른 동물 오검출", Verdict.countLabel(0, 0, 0, 1));
        assertEquals("개수 부족", Verdict.countLabel(1, 2, 0, -1));
    }

    @Test void aSinglePredictionCannotMatchTwoIdenticalPets() {
        var pet = new Verdict.Pet("dog", "퍼그");
        var actual = Verdict.compare(0, 2, 0, 1, List.of(pet, pet), List.of(pet), Map.of(), UnaryOperator.identity());
        assertEquals(1, actual.correct());
        assertEquals("일부 일치 1/2", actual.breedLabel());
    }

    @Test void parsesSpeciesAndPosition() {
        var pets = GroundTruthParser.parse("고양이1(왼쪽: 벵갈); 개1(퍼그)", 1, 1);
        assertEquals(new GroundTruthParser.Entry("cat", "벵갈", "왼쪽"), pets.get(0));
        assertEquals("", pets.get(1).position());
        assertThrows(IllegalArgumentException.class, () -> GroundTruthParser.parse("개(퍼그)", 2, 0));
    }
}
