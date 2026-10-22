package com.signalops.core.incident.repository;

import com.signalops.core.incident.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, String> {
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records (
                idempotency_key,
                request_hash,
                incident_id,
                created_at,
                expires_at
            )
            VALUES (
                :key,
                :requestHash,
                :incidentId,
                :createdAt,
                :expiresAt
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int claimKey(
            @Param("key") String key,
            @Param("requestHash") String requestHash,
            @Param("incidentId") java.util.UUID incidentId,
            @Param("createdAt") Instant createdAt,
            @Param("expiresAt") Instant expiresAt
    );
}
