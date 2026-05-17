package com.synapse.backend.docs;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocsController {

    @GetMapping("/")
    public String docs() {
        return "redirect:/swagger-ui.html";
    }
}
