
package com.signalops.core.incident.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
        // Required by JPA.
    }

    public IdempotencyRecord(
            String idempotencyKey,
            String requestHash,
            UUID incidentId,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.incidentId = incidentId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public UUID getIncidentId() { return incidentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
