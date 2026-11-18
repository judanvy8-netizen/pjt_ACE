package kr.ace.web.benchmark.controller;

import kr.ace.web.benchmark.service.BenchmarkService;
import kr.ace.web.media.model.PhotoNavigationDO;
import kr.ace.web.media.model.PhotoNavigationDO.Choice;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class BenchmarkController {
    private final BenchmarkService benchmark;

    public BenchmarkController(BenchmarkService benchmark) {
        this.benchmark = benchmark;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("summaries", benchmark.summaries());
        model.addAttribute("standardBaseline", benchmark.standardBaseline());
        return "home";
    }

    @GetMapping("/versions")
    public String versions(Model model) {
        model.addAttribute("summaries", benchmark.summaries());
        model.addAttribute("standardBaseline", benchmark.standardBaseline());
        model.addAttribute("photos", benchmark.truth().values());
        return "versions";
    }

    @GetMapping("/versions/photos/{filename}")
    public String photo(@PathVariable String filename, Model model) {
        model.addAttribute("photo", benchmark.photo(filename)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        var choices = benchmark.truth().keySet().stream().sorted()
                .map(name -> new Choice(name, name)).toList();
        model.addAttribute("navigation", PhotoNavigationDO.of("/versions/photos", choices, filename));
        return "version-photo";
    }

    @GetMapping("/versions/images/{version}/{filename}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String version, @PathVariable String filename) {
        var path = benchmark.image(version, filename)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var resource = new FileSystemResource(path);
        return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(org.springframework.http.MediaType.IMAGE_JPEG))
                .body(resource);
    }

    @GetMapping("/performance")
    public String performance(Model model) {
        model.addAttribute("performance", benchmark.performance());
        model.addAttribute("standardBaseline", benchmark.standardBaseline());
        return "performance";
    }

    @GetMapping("/benchmark")
    public String verification(Model model) {
        model.addAttribute("tables", java.util.List.of(benchmark.verification(false), benchmark.verification(true)));
        return "benchmark";
    }
}
