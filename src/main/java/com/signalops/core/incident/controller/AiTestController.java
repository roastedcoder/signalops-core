
package com.signalops.core.incident.controller;

import com.signalops.core.incident.dto.AiTriageDecision;
import com.signalops.core.incident.service.AiConnectionTestService;
import com.signalops.core.incident.service.AiTriageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiTestController {

    private final AiConnectionTestService aiConnectionTestService;
    private final AiTriageService aiTriageService;

    public AiTestController(
            AiConnectionTestService aiConnectionTestService, AiTriageService aiTriageService) {
        this.aiConnectionTestService = aiConnectionTestService;
        this.aiTriageService = aiTriageService;
    }

    @GetMapping("/api/ai/test")
    public String test(@RequestParam(defaultValue = "Explain a database timeout in one sentence.")
                       String question) {
        return aiConnectionTestService.ask(question);
    }

    @GetMapping("/api/ai/triage-test")
    public AiTriageDecision triageTest() {
        return aiTriageService.triage(
                "Checkout API latency spike",
                "p95 latency increased from 200ms to 2400ms. "
                        + "Database connection pool usage is 98%.",
                "HIGH",
                "checkout-service"
        );
    }
}
