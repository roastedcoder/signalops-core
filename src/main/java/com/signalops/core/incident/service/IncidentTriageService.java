
package com.signalops.core.incident.service;

import com.signalops.core.incident.dto.AiTriageDecision;
import com.signalops.core.incident.model.OutboxEvent;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class IncidentTriageService {

    private static final Set<String> ALLOWED_SEVERITIES =
            Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final AiTriageService aiTriageService;

    public IncidentTriageService(AiTriageService aiTriageService) {
        this.aiTriageService = aiTriageService;
    }

    public TriageDecision triage(OutboxEvent event) {
        if (event == null || event.getPayload() == null) {
            throw new IllegalArgumentException(
                    "Outbox event and payload must not be null");
        }

        Map<String, Object> payload = event.getPayload();

        String title = requiredText(payload, "title");
        String severity = requiredText(payload, "severity")
                .toUpperCase(java.util.Locale.ROOT);

        if (!ALLOWED_SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException(
                    "Invalid incident severity: " + severity);
        }

        String description = optionalText(payload, "description");
        String source = optionalText(payload, "source");

        AiTriageDecision decision = aiTriageService.triage(
                title,
                description,
                severity,
                source
        );

        if (decision == null) {
            throw new IllegalStateException(
                    "AI triage returned no decision");
        }

        return new TriageDecision(
                decision.summary(),
                decision.recommendedSeverity(),
                decision.rationale()
        );
    }

    private static String requiredText(
            Map<String, Object> payload, String field) {

        Object value = payload.get(field);

        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or invalid required payload field: " + field);
        }

        return text.trim();
    }

    private static String optionalText(
            Map<String, Object> payload, String field) {

        Object value = payload.get(field);

        if (value == null) {
            return "Not provided";
        }

        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(
                    "Invalid payload field: " + field);
        }

        return text.isBlank() ? "Not provided" : text.trim();
    }

    public record TriageDecision(
            String summary,
            String recommendedSeverity,
            String rationale
    ) {
    }
}
