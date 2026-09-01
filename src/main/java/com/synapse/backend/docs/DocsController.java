package com.synapse.backend.docs;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile("!prod")
public class DocsController {

    @GetMapping("/")
    public String docs() {
        return "redirect:/swagger-ui.html";
    }
}
