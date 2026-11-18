package kr.ace.web.benchmark.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class BenchmarkRepository {
    private static final Logger LOG = LoggerFactory.getLogger(BenchmarkRepository.class);
    private final Path root;
    private final Path baseline;

    public BenchmarkRepository(@Value("${ace.project-root}") String root,
            @Value("${ace.baseline-dir}") String baseline) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.baseline = this.root.resolve(baseline).normalize();
    }

    private Path versionDir(String version) {
        return Files.isRegularFile(baseline.resolve("STANDARD_VERIFIED"))
                ? baseline.resolve(version) : baseline.resolve("legacy-original-res").resolve(version);
    }

    public boolean standardBaseline() {
        return Files.isRegularFile(baseline.resolve("STANDARD_VERIFIED"));
    }

    public CsvTable resultCsv(String version, String name) {
        return read(versionDir(version).resolve(name));
    }

    public CsvTable csv(String path) {
        return read(root.resolve(path));
    }

    private CsvTable read(Path path) {
        try {
            if (!Files.isRegularFile(path) || !path.toRealPath().startsWith(root.toRealPath())) {
                return new CsvTable(java.util.List.of(), java.util.List.of(), 0);
            }
            return CsvTable.read(path);
        } catch (IOException e) {
            LOG.warn("벤치마크 CSV를 읽지 못했습니다: {}", path, e);
            return new CsvTable(java.util.List.of(), java.util.List.of(), 1);
        }
    }

    public Optional<Path> image(String version, String name) {
        Path path = ("original".equals(version) ? root.resolve("data/images").resolve(name)
                : "holdout".equals(version) ? root.resolve("data/holdout/images").resolve(name)
                : versionDir(version).resolve(name.substring(0, name.lastIndexOf('.')) + "_pets.jpg")).normalize();
        try {
            if (path.startsWith(root) && Files.isRegularFile(path)
                    && path.toRealPath().startsWith(root.toRealPath())) {
                return Optional.of(path);
            }
        } catch (IOException e) {
            LOG.debug("비교 이미지를 읽지 못했습니다: {}", path, e);
        }
        return Optional.empty();
    }
}
