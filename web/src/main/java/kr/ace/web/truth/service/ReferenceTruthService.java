package kr.ace.web.truth.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import kr.ace.web.benchmark.service.BenchmarkService;
import kr.ace.web.media.service.MediaService;
import kr.ace.web.truth.model.ReferenceTruthDO;
import org.springframework.stereotype.Service;

@Service
public class ReferenceTruthService {
    private final BenchmarkService benchmark;
    private final MediaService storage;

    public ReferenceTruthService(BenchmarkService benchmark, MediaService storage) {
        this.benchmark = benchmark;
        this.storage = storage;
    }

    public ReferenceTruthDO find(String filename, String sourcePath) {
        var truth = benchmark.referenceTruth().get(filename);
        if (truth == null) {
            return new ReferenceTruthDO("missing", null);
        }
        var reference = benchmark.referenceImage(filename);
        if (reference.isEmpty()) {
            return new ReferenceTruthDO("unavailable", null);
        }
        try {
            Path uploaded = storage.file(sourcePath).getFile().toPath();
            return MessageDigest.isEqual(hash(uploaded), hash(reference.get()))
                    ? new ReferenceTruthDO("matched", truth)
                    : new ReferenceTruthDO("different", null);
        } catch (IOException e) {
            return new ReferenceTruthDO("unavailable", null);
        }
    }

    private byte[] hash(Path path) throws IOException {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (var stream = new DigestInputStream(Files.newInputStream(path), sha256)) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return sha256.digest();
    }
}
