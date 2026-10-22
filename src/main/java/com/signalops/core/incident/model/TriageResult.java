
package com.signalops.core.incident.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "triage_results")
public class TriageResult {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "triage_summary", nullable = false)
    private String triageSummary;

    @Column(name = "recommended_severity", nullable = false)
    private String recommendedSeverity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "rationale", nullable = false, length = 1000)
    private String rationale;

    protected TriageResult() {
    }

    public TriageResult(
            UUID eventId,
            UUID incidentId,
            String triageSummary,
            String recommendedSeverity,
            String rationale
    ) {
        this.eventId = eventId;
        this.incidentId = incidentId;
        this.triageSummary = triageSummary;
        this.recommendedSeverity = recommendedSeverity;
        this.createdAt = Instant.now();
        this.rationale = rationale;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getTriageSummary() {
        return triageSummary;
    }

    public String getRecommendedSeverity() {
        return recommendedSeverity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getRationale() {
        return rationale;
    }
}
