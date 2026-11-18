package kr.ace.web.dataset.controller;

import java.io.IOException;
import java.util.UUID;
import kr.ace.web.dataset.model.RoundDO.Photo;
import kr.ace.web.dataset.model.RoundDO.Progress;
import kr.ace.web.media.model.PhotoNavigationDO;
import kr.ace.web.review.service.ReviewService;
import kr.ace.web.review.service.PhotoNoteService;
import kr.ace.web.dataset.service.DatasetService;
import kr.ace.web.truth.service.ReferenceTruthService;
import kr.ace.web.benchmark.service.BenchmarkService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DatasetController {
    private final DatasetService service;

    private final ReferenceTruthService referenceTruth;
    private final BenchmarkService benchmark;
    private final ReviewService reviews;
    private final PhotoNoteService photoNotes;

    public DatasetController(DatasetService service, ReferenceTruthService referenceTruth, BenchmarkService benchmark,
                             ReviewService reviews, PhotoNoteService photoNotes) {
        this.service = service;
        this.referenceTruth = referenceTruth;
        this.benchmark = benchmark;
        this.reviews = reviews;
        this.photoNotes = photoNotes;
    }

    @GetMapping({"/analyze", "/versions/datasets"})
    public String index(Model model) {
        model.addAttribute("datasets", service.datasets());
        return "datasets";
    }

    @PostMapping("/datasets")
    public String register(@RequestParam String name, @RequestParam("photo") MultipartFile[] photos,
                           RedirectAttributes flash) {
        try {
            return "redirect:/datasets/" + service.register(name, photos);
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("notice", e.getMessage());
        } catch (IOException | DataAccessException e) {
            flash.addFlashAttribute("notice", "등록에 실패했습니다. 저장 폴더와 MySQL 연결을 확인해 주세요.");
        }
        return "redirect:/analyze";
    }

    @GetMapping("/datasets/{id}")
    public String dataset(@PathVariable String id, Model model) {
        model.addAttribute("dataset", service.dataset(id));
        model.addAttribute("filenames", service.filenames(id));
        model.addAttribute("rounds", service.rounds(id));
        model.addAttribute("token", UUID.randomUUID().toString());
        return "dataset";
    }

    @PostMapping("/datasets/{id}/rounds")
    public String start(@PathVariable String id, @RequestParam String token, RedirectAttributes flash) {
        try {
            return "redirect:/rounds/" + service.start(id, token);
        } catch (ResponseStatusException e) {
            if (!e.getStatusCode().equals(HttpStatus.SERVICE_UNAVAILABLE)) throw e;
            flash.addFlashAttribute("notice", e.getReason());
            return "redirect:/datasets/" + id;
        }
    }

    @GetMapping("/rounds/{id}")
    public String round(@PathVariable String id, Model model) {
        model.addAttribute("round", service.round(id));
        var photos = service.photos(id, null);
        model.addAttribute("photos", photos);
        var tuning = benchmark.truth().keySet();
        var reference = benchmark.referenceTruth().keySet();
        var groups = new java.util.LinkedHashMap<String, java.util.List<kr.ace.web.dataset.model.RoundDO.Score>>();
        var tuningPhotos = photos.stream().filter(p -> tuning.contains(p.filename())).toList();
        var holdout = photos.stream().filter(p -> reference.contains(p.filename()) && !tuning.contains(p.filename())).toList();
        if (!tuningPhotos.isEmpty()) groups.put("튜닝 기준 사진", service.scores(tuningPhotos));
        if (!holdout.isEmpty()) groups.put("홀드아웃", service.scores(holdout));
        model.addAttribute("scoreGroups", groups);
        model.addAttribute("otherCount", photos.stream().filter(p -> !reference.contains(p.filename())).count());
        model.addAttribute("progress", service.progress(id));
        return "round";
    }

    @GetMapping("/rounds/{id}/photos/{inputId}")
    public String photo(@PathVariable String id, @PathVariable long inputId, Model model) {
        model.addAttribute("round", service.round(id));
        var photo = requirePhoto(id, inputId);
        model.addAttribute("photo", photo);
        model.addAttribute("notes", photoNotes.forPhoto(inputId));
        var original = photo.items().get(0).run();
        var reference = referenceTruth.find(original.filename(), original.sourcePath());
        model.addAttribute("original", original);
        model.addAttribute("reference", reference);
        model.addAttribute("reviewed", service.reviewed(inputId));
        model.addAttribute("reviewable", reference.truth() != null
                && photo.items().stream().anyMatch(item -> "SUCCEEDED".equals(item.run().status())));
        model.addAttribute("navigation", PhotoNavigationDO.of("/rounds/" + id + "/photos",
                service.photoChoices(id), Long.toString(inputId)));
        var verdicts = java.util.Map.<String, kr.ace.web.evaluation.Verdict>of();
        if (reference.truth() != null) {
            try {
                verdicts = service.previewVerdicts(photo, reference.truth());
            } catch (IllegalArgumentException e) {
                model.addAttribute("truthWarning", e.getMessage());
                model.addAttribute("reviewable", false);
            }
        }
        model.addAttribute("verdicts", verdicts);
        return "round-photo";
    }

    @PostMapping("/rounds/{id}/photos/{inputId}/notes")
    public String saveNotes(@PathVariable String id, @PathVariable long inputId,
            @RequestParam(defaultValue = "") String mainNote,
            @RequestParam(defaultValue = "") String main1Note,
            @RequestParam(defaultValue = "") String main2Note,
            @RequestParam(defaultValue = "") String overallNote, RedirectAttributes flash) {
        service.round(id);
        requirePhoto(id, inputId);
        try {
            photoNotes.save(inputId, java.util.Map.of("main", mainNote, "main-1", main1Note,
                    "main-2", main2Note, "overall", overallNote));
            flash.addFlashAttribute("notice", "비고를 저장했습니다.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("notice", e.getMessage());
        } catch (DataAccessException e) {
            flash.addFlashAttribute("notice", "비고 저장에 실패했습니다. DB 연결과 마이그레이션을 확인해 주세요.");
        }
        return "redirect:/rounds/" + id + "/photos/" + inputId;
    }

    @PostMapping("/rounds/{id}/photos/{inputId}/review")
    public String reviewPhoto(@PathVariable String id, @PathVariable long inputId, RedirectAttributes flash) {
        service.round(id);
        var photo = requirePhoto(id, inputId);
        try {
            var run = photo.items().stream().map(item -> item.run())
                    .filter(item -> "SUCCEEDED".equals(item.status())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("성공한 분석 결과가 있어야 검수할 수 있습니다."));
            reviews.save(run.runId());
            flash.addFlashAttribute("notice", "사진 검수를 저장했습니다.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("notice", e.getMessage());
        } catch (DataAccessException e) {
            flash.addFlashAttribute("notice", "검수를 저장하지 못했습니다. MySQL 연결을 확인하고 다시 시도해 주세요.");
        }
        return "redirect:/rounds/" + id + "/photos/" + inputId;
    }

    private Photo requirePhoto(String roundId, long inputId) {
        return service.photos(roundId, inputId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/rounds/{id}/progress")
    @ResponseBody
    public Progress progress(@PathVariable String id) {
        service.round(id);
        return service.progress(id);
    }

    @GetMapping("/api/rounds/{id}/runs")
    @ResponseBody
    public java.util.List<kr.ace.web.dataset.model.RoundDO.Photo> runs(@PathVariable String id) {
        service.round(id);
        return service.photos(id, null);
    }

    @PostMapping("/rounds/{id}/resume")
    public String resume(@PathVariable String id, RedirectAttributes flash) {
        try {
            service.resume(id);
        } catch (ResponseStatusException e) {
            showOperationError(flash, e);
        }
        return "redirect:/rounds/" + id;
    }

    @PostMapping("/rounds/{id}/retry/{runId}")
    public String retry(@PathVariable String id, @PathVariable String runId, RedirectAttributes flash) {
        try {
            service.retry(id, runId);
        } catch (ResponseStatusException e) {
            showOperationError(flash, e);
        }
        return "redirect:/rounds/" + id;
    }
    @PostMapping("/rounds/{id}/retry-failed")
    public String retryFailed(@PathVariable String id, RedirectAttributes flash) {
        try {
            service.retryFailed(id);
        } catch (ResponseStatusException e) {
            showOperationError(flash, e);
        }
        return "redirect:/rounds/" + id;
    }

    private void showOperationError(RedirectAttributes flash, ResponseStatusException e) {
        if (!e.getStatusCode().equals(HttpStatus.CONFLICT)
                && !e.getStatusCode().equals(HttpStatus.SERVICE_UNAVAILABLE)) throw e;
        flash.addFlashAttribute("notice", e.getReason());
    }
}
