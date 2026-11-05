package com.signalops.core.evidence.logs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "application_logs")
public class ApplicationLog {

    @Id
    private UUID id;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 10)
    private String level;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "incident_id")
    private UUID incidentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes = new HashMap<>();

    protected ApplicationLog() {
        // Required by JPA.
    }

    public ApplicationLog(
            UUID id,
            String serviceName,
            Instant occurredAt,
            String level,
            String message,
            UUID incidentId,
            Map<String, Object> attributes
    ) {
        this.id = id;
        this.serviceName = serviceName;
        this.occurredAt = occurredAt;
        this.level = level;
        this.message = message;
        this.incidentId = incidentId;
        this.attributes = attributes == null
                ? new HashMap<>()
                : new HashMap<>(attributes);
    }

    public UUID getId() {
        return id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public Map<String, Object> getAttributes() {
        return Map.copyOf(attributes);
    }
}
