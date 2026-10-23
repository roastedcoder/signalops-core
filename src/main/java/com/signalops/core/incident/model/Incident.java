package com.signalops.core.incident.model;

import com.signalops.core.incident.enums.IncidentStatus;
import com.signalops.core.incident.enums.Severity;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name="incidents")
public class Incident {
    @Getter
    @Id
    private UUID id;

    @Getter
    @Column(nullable = false, length = 200)
    private String title;

    @Getter
    @Column(columnDefinition = "TEXT")
    private String description;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Getter
    @Column(nullable = false, length = 100)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Getter
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Getter
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Getter
    @Version
    @Column(nullable = false)
    private long version;

    protected Incident() {
        // Required by JPA.
    }

    public Incident(
            UUID id,
            String title,
            String description,
            Severity severity,
            String source,
            Map<String, Object> metadata
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.source = source;
        this.metadata = metadata == null
                ? new HashMap<>()
                : new HashMap<>(metadata);
    }

    public Map<String, Object> getMetadata() {
        return Map.copyOf(metadata);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void changeStatus(IncidentStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Status must not be null");
        }

        boolean validTransition = switch (this.status) {
            case OPEN ->
                    newStatus == IncidentStatus.ACKNOWLEDGED;
            case ACKNOWLEDGED ->
                    newStatus == IncidentStatus.INVESTIGATING
                            || newStatus == IncidentStatus.RESOLVED;
            case INVESTIGATING ->
                    newStatus == IncidentStatus.ACKNOWLEDGED
                            || newStatus == IncidentStatus.RESOLVED;
            case RESOLVED -> false;
        };

        if (!validTransition) {
            throw new IllegalStateException(
                    "Invalid incident status transition: "
                            + this.status + " -> " + newStatus
            );
        }

        this.status = newStatus;
    }

}
