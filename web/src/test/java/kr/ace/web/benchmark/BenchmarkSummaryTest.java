package kr.ace.web.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import kr.ace.web.benchmark.model.BenchmarkDO.SpeciesCount;
import kr.ace.web.benchmark.repository.BenchmarkRepository;
import kr.ace.web.benchmark.service.BenchmarkService;
import kr.ace.web.evaluation.BreedRules;
import org.junit.jupiter.api.Test;

class BenchmarkSummaryTest {
    @Test
    void summarizesTheFortyReferencePhotosAndSpeciesCounts() {
        var repository = new BenchmarkRepository(Path.of("..").toAbsolutePath().normalize().toString());
        var summaries = new BenchmarkService(repository, new BreedRules(repository)).summaries();

        assertEquals(List.of(31, 31, 34, 37), summaries.stream().map(item -> item.correct()).toList());
        assertEquals(List.of(40, 40, 40, 40), summaries.stream().map(item -> item.total()).toList());
        assertEquals(List.of(9, 9, 6, 3), summaries.stream().map(item -> item.mismatches().size()).toList());
        assertEquals(List.of(2, 2, 2, 0), summaries.stream().map(item -> item.bothWrongPhotos()).toList());
        assertEquals(List.of(11, 11, 8, 3), summaries.stream().map(item -> item.countErrors()).toList());
        assertEquals(List.of(new SpeciesCount(19, 21, 22), new SpeciesCount(19, 21, 22),
                new SpeciesCount(19, 21, 21), new SpeciesCount(20, 21, 20)),
                summaries.stream().map(item -> item.cats()).toList());
        assertEquals(List.of(new SpeciesCount(27, 29, 31), new SpeciesCount(27, 29, 31),
                new SpeciesCount(27, 29, 29), new SpeciesCount(28, 29, 29)),
                summaries.stream().map(item -> item.dogs()).toList());
        assertEquals(List.of(0, 24, 33, 36), summaries.stream().map(item -> item.breedCorrect()).toList());
        assertEquals(List.of(0, 50, 50, 50), summaries.stream().map(item -> item.breedTotal()).toList());
    }
}
