package kr.ace.web.review.controller;

import kr.ace.web.review.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @PostMapping("/history/{runId}/truth")
    public String save(@PathVariable String runId, RedirectAttributes flash) {
        try {
            service.save(runId);
            flash.addFlashAttribute("notice", "CSV 정답을 검수했습니다. 성공한 결과의 평가를 확인할 수 있습니다.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("notice", e.getMessage());
        }
        return "redirect:/history/" + runId;
    }
}
