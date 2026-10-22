package com.signalops.core.incident.model;

import com.signalops.core.incident.enums.OutboxStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "locked_by", length = 128)
    private String lockedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    protected OutboxEvent() {
        // Required by JPA.
    }

    public OutboxEvent(
            UUID id,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload
    ) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = Map.copyOf(payload);
        this.attemptCount = 0;
        this.nextAttemptAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getPayload() { return Map.copyOf(payload); }
    public OutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLockedUntil() { return lockedUntil; }
    public String getLockedBy() { return lockedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }


    public void markProcessing(String workerId, Instant leaseExpiry) {
        this.status = OutboxStatus.PROCESSING;
        this.attemptCount++;
        this.lockedBy = workerId;
        this.lockedUntil = leaseExpiry;
        this.lastError = null;
    }

    public void markPublished(Instant publishedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.lockedBy = null;
        this.lockedUntil = null;
        this.lastError = null;
    }

    public void markFailed(
            Instant nextAttemptAt,
            String errorMessage
    ) {
        this.status = OutboxStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = errorMessage;
        this.lockedBy = null;
        this.lockedUntil = null;
    }

    public void markPermanentlyFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.lastError = errorMessage;
        this.lockedBy = null;
        this.lockedUntil = null;
    }


    public void replay(Instant now) {
        if (status != OutboxStatus.FAILED) {
            throw new IllegalStateException(
                    "Only FAILED events can be replayed: " + id
            );
        }

        status = OutboxStatus.PENDING;
        attemptCount = 0;
        nextAttemptAt = now;
        lockedBy = null;
        lockedUntil = null;
        lastError = null;
        publishedAt = null;
    }

}
