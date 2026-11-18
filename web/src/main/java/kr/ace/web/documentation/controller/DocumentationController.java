package kr.ace.web.documentation.controller;

import kr.ace.web.documentation.service.SourceStructureService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DocumentationController {
    private final SourceStructureService structure;

    public DocumentationController(SourceStructureService structure) { this.structure = structure; }

    @GetMapping("/source-structure")
    public String source(@RequestParam(defaultValue = "java") String language,
                         @RequestParam(defaultValue = "") String group, Model model) {
        boolean python = "python".equals(language);
        var groups = structure.groups(python);
        var selected = groups.stream().filter(item -> item.id().equals(group)).findFirst().orElse(groups.get(0));
        model.addAttribute("language", python ? "python" : "java");
        model.addAttribute("groups", groups);
        model.addAttribute("selected", selected);
        return "source-structure";
    }

    @GetMapping("/technology")
    public String technology() { return "technology"; }
}
