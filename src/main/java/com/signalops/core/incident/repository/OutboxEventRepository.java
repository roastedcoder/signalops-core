
package com.signalops.core.incident.repository;

import com.signalops.core.incident.model.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {


    @Query(value = """
        SELECT *
        FROM outbox_events
        WHERE (
            status = 'PENDING'
            AND next_attempt_at <= :now
        )
        OR (
            status = 'PROCESSING'
            AND locked_until <= :now
        )
        ORDER BY created_at
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> claimPendingEvents(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e WHERE e.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") UUID id);


    @Query(value = """
        SELECT COUNT(*)
        FROM outbox_events
        WHERE status = 'PENDING'
        """, nativeQuery = true)
    long countPendingEvents();


    @Query(value = """
        SELECT COUNT(*)
        FROM outbox_events
        WHERE status = 'PROCESSING'
        """, nativeQuery = true)
    long countProcessingEvents();

    @Query(value = """
        SELECT COUNT(*)
        FROM outbox_events
        WHERE status = 'FAILED'
        """, nativeQuery = true)
    long countFailedEvents();


}
