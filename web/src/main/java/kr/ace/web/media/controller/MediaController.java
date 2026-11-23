package kr.ace.web.media.controller;

import java.io.IOException;
import kr.ace.web.analysis.service.AnalysisService;
import kr.ace.web.media.service.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class MediaController {
    private final AnalysisService analysis;
    private final MediaService storage;

    public MediaController(AnalysisService analysis, MediaService storage) {
        this.analysis = analysis;
        this.storage = storage;
    }

    @GetMapping("/media/{runId}/{kind}")
    public ResponseEntity<?> photo(@PathVariable String runId, @PathVariable String kind) {
        var run = analysis.require(runId);
        String path = switch (kind) {
            case "original" -> run.sourcePath();
            case "result" -> run.resultPath();
            default -> null;
        };
        if (path == null || ("result".equals(kind) && !"SUCCEEDED".equals(run.status()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            String type = path.substring(path.lastIndexOf('.') + 1);
            return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
                    .contentType(MediaType.parseMediaType("image/" + ("jpg".equals(type) ? "jpeg" : type)))
                    .body(storage.file(path));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
