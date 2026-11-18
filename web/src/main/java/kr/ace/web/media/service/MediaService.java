package kr.ace.web.media.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import kr.ace.web.media.entity.InputMedia;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import kr.ace.web.media.model.UploadDO;

@Service
public class MediaService {
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "bmp", "webp");
    private final Path root;

    public MediaService(@Value("${ace.storage}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public InputMedia save(UploadDO upload) throws IOException {
        return save(upload, null);
    }

    public InputMedia save(UploadDO upload, String datasetId) throws IOException {
        var photo = upload.photo();
        if (photo.isEmpty() || photo.getSize() > 15L * 1024 * 1024) {
            throw new IllegalArgumentException("15MB 이하의 사진을 선택해 주세요.");
        }
        String name = photo.getOriginalFilename();
        if (name == null) {
            throw new IllegalArgumentException("파일명이 필요합니다.");
        }
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (name.length() > 255 || !EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("JPG, PNG, BMP, WebP 사진을 선택해 주세요.");
        }
        String runId = UUID.randomUUID().toString();
        String prefix = datasetId == null ? "inbox/" : "datasets/" + datasetId + "/";
        String relative = prefix + runId + "/original." + extension;
        Path destination = root.resolve(relative);
        Files.createDirectories(destination.getParent());
        if (!destination.getParent().toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("저장 경로가 올바르지 않습니다.");
        }
        try (var stream = photo.getInputStream()) {
            Files.copy(stream, destination);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
        return new InputMedia(runId, name, relative);
    }

    public FileSystemResource file(String relative) throws IOException {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)
                || !target.toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("이미지를 찾을 수 없습니다.");
        }
        return new FileSystemResource(target);
    }
}
