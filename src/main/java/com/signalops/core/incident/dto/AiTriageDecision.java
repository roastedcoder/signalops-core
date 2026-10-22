
package com.signalops.core.incident.dto;

import java.util.Set;

public record AiTriageDecision(
        String summary,
        String recommendedSeverity,
        String rationale
) {
    private static final Set<String> ALLOWED_SEVERITIES =
            Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public AiTriageDecision {
        requireText(summary, "summary", 500);
        requireText(recommendedSeverity, "recommendedSeverity", 20);
        requireText(rationale, "rationale", 1000);

        if (!ALLOWED_SEVERITIES.contains(recommendedSeverity)) {
            throw new IllegalArgumentException(
                    "Invalid recommended severity: "
                            + recommendedSeverity);
        }
    }

    private static void requireText(
            String value, String field, int maxLength) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }

        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " exceeds maximum length of "
                            + maxLength);
        }
    }
}
