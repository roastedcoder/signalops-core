
package com.signalops.core.incident.dto;

import com.signalops.core.incident.model.TriageResult;

import java.time.Instant;
import java.util.UUID;

public record TriageResultResponse(
        UUID incidentId,
        String summary,
        String recommendedSeverity,
        Instant createdAt,
        String rationale
) {
    public static TriageResultResponse from(TriageResult result) {
        return new TriageResultResponse(
                result.getIncidentId(),
                result.getTriageSummary(),
                result.getRecommendedSeverity(),
                result.getCreatedAt(),
                result.getRationale()
        );
    }
}
