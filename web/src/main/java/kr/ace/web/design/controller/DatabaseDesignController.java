package kr.ace.web.design.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DatabaseDesignController {
    @GetMapping("/db-design")
    public String design() {
        return "database-design";
    }
}
