package kr.ace.web.analysis.controller;

import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.Set;
import kr.ace.web.analysis.service.AnalysisService;
import kr.ace.web.truth.service.ReferenceTruthService;
import kr.ace.web.media.model.UploadDO;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AnalysisController {
    private final AnalysisService service;

    private final ReferenceTruthService referenceTruth;


    public AnalysisController(AnalysisService service, ReferenceTruthService referenceTruth) {
        this.service = service;
        this.referenceTruth = referenceTruth;
    }

    @PostMapping("/analyze")
    public String upload(@RequestParam("photo") MultipartFile[] photos, RedirectAttributes flash) {
        if (photos.length != 1) {
            flash.addFlashAttribute("notice", "여러 장을 분석하려면 사진 분석에서 데이터셋을 등록해 주세요.");
            return "redirect:/analyze";
        }
        try {
            return "redirect:/history/" + service.upload(new UploadDO(photos[0]));
        } catch (AnalysisService.ResultSaveException e) {
            flash.addFlashAttribute("notice", "분석 기록은 만들어졌지만 결과 저장에 실패했습니다. DB 확인 후 재시도해 주세요.");
            return "redirect:/history/" + e.runId();
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("notice", e.getMessage());
        } catch (IOException e) {
            flash.addFlashAttribute("notice", "사진을 저장할 수 없습니다. 공유 폴더와 쓰기 권한을 확인해 주세요.");
        } catch (DataAccessException e) {
            flash.addFlashAttribute("notice", "MySQL에 저장할 수 없습니다. DB 상태를 확인한 뒤 다시 시도해 주세요.");
        }
        return "redirect:/analyze";
    }

    @GetMapping("/history")
    public String history(@RequestParam(defaultValue = "all") String filter, Model model) {
        String selected = historyFilter(filter);
        var entries = service.history(selected);
        model.addAttribute("entries", entries);
        model.addAttribute("hasFailures", entries.stream().anyMatch(entry -> "FAILED".equals(entry.run().status())));
        model.addAttribute("filter", selected);
        model.addAttribute("summary", service.historySummary());
        return "history";
    }

    @PostMapping("/history/failed/delete")
    public String deleteFailed(@RequestParam(required = false) List<String> runIds,
                               @RequestParam(defaultValue = "all") String filter, RedirectAttributes flash) {
        try {
            int deleted = service.dismissFailed(runIds);
            flash.addFlashAttribute("notice", deleted == 0 ? "삭제할 실패 기록이 없습니다."
                    : "실패 기록 " + deleted + "건을 분석 이력에서 삭제했습니다.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("notice", "삭제할 실패 기록을 다시 선택해 주세요.");
        } catch (DataAccessException e) {
            flash.addFlashAttribute("notice", "삭제하지 못했습니다. MySQL 연결을 확인해 주세요.");
        }
        flash.addAttribute("filter", historyFilter(filter));
        return "redirect:/history";
    }

    private String historyFilter(String filter) {
        return Set.of("matched", "errors", "failed").contains(filter) ? filter : "all";
    }

    @GetMapping("/history/{runId}")
    public String detail(@PathVariable String runId, Model model) {
        var detail = service.detail(runId);
        if ("DISMISSED".equals(detail.run().status())) return "redirect:/history";
        model.addAttribute("run", detail.run());
        model.addAttribute("pets", detail.objects());
        model.addAttribute("evaluations", detail.evaluations());
        model.addAttribute("latest", detail.latest());
        model.addAttribute("expected", detail.expected());
        model.addAttribute("roundId", detail.run().roundId());
        if ("SUCCEEDED".equals(detail.run().status())) {
            model.addAttribute("reference", referenceTruth.find(detail.run().filename(), detail.run().sourcePath()));
        }
        return "detail";
    }

    @PostMapping("/history/{runId}/retry")
    public String retry(@PathVariable String runId, RedirectAttributes flash) {
        try {
            service.analyze(runId);
        } catch (kr.ace.web.analysis.client.AiClient.AiUnavailableException e) {
            flash.addFlashAttribute("notice", e.getMessage());
        } catch (DataAccessException e) {
            flash.addFlashAttribute("notice", "결과 저장에 실패했습니다. MySQL을 확인한 후 같은 실행에서 다시 시도해 주세요.");
        }
        return "redirect:/history/" + runId;
    }

    @GetMapping("/api/runs/{runId}")
    @ResponseBody
    public Map<String, Object> result(@PathVariable String runId) {
        var run = service.require(runId);
        return Map.of("run", run, "objects", service.objects(runId));
    }
}
