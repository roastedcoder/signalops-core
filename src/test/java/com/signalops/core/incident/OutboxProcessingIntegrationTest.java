
package com.signalops.core.incident;

import com.signalops.core.incident.enums.OutboxStatus;
import com.signalops.core.incident.service.OutboxClaimService;
import com.signalops.core.incident.service.OutboxProcessingService;
import com.signalops.core.incident.service.OutboxReplayService;
import com.signalops.core.incident.service.OutboxRetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class OutboxProcessingIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:14-alpine");

    @Autowired
    private OutboxProcessingService outboxProcessingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxClaimService outboxClaimService;

    @Autowired
    private OutboxRetryService outboxRetryService;

    @Autowired
    private OutboxReplayService outboxReplayService;

    @Test
    void triageInsertFailureRollsBackPublishedStatus() {
        UUID incidentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String workerId = "integration-test-worker";

        // Create a minimal incident row for the foreign key.
        jdbcTemplate.update("""
            INSERT INTO incidents (
                id, title, description, severity, status, source,
                created_at, updated_at, version
            )
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)
            """,
                incidentId,
                "Rollback test",
                "Testing atomicity",
                "HIGH",
                "OPEN",
                "integration-test"
        );

        // Create an outbox event and mark it as claimed.
        jdbcTemplate.update("""
            INSERT INTO outbox_events (
                id, aggregate_id, event_type, payload, status,
                attempt_count, next_attempt_at, created_at,
                locked_by, locked_until
            )
            VALUES (?, ?, ?, '{}'::jsonb, 'PROCESSING',
                    1, NOW(), NOW(), ?, NOW() + INTERVAL '60 seconds')
            """,
                eventId,
                incidentId,
                "INCIDENT_CREATED",
                workerId
        );

        // NULL violates triage_results.recommended_severity NOT NULL.
        assertThrows(DataIntegrityViolationException.class, () ->
                outboxProcessingService.completeProcessing(
                        eventId,
                        workerId,
                        "Test summary",
                        null,
                        "Test rationale"
                )
        );

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE id = ?",
                String.class,
                eventId
        );

        assertEquals("PROCESSING", status);

        Integer triageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM triage_results WHERE event_id = ?",
                Integer.class,
                eventId
        );

        assertEquals(0, triageCount);
    }

    @Test
    void expiredLeaseCanBeReclaimedByAnotherWorker() {
        UUID incidentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // Create the incident required by the outbox foreign key.
        jdbcTemplate.update("""
        INSERT INTO incidents (
            id, title, description, severity, status, source,
            created_at, updated_at, version
        )
        VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)
        """,
                incidentId,
                "Worker crash test",
                "Testing lease recovery",
                "HIGH",
                "OPEN",
                "integration-test"
        );

        // Create an event whose processing lease has already expired.
        jdbcTemplate.update("""
        INSERT INTO outbox_events (
            id, aggregate_id, event_type, payload, status,
            attempt_count, next_attempt_at, created_at,
            locked_by, locked_until
        )
        VALUES (?, ?, ?, '{}'::jsonb, 'PROCESSING',
                1, NOW(), NOW(), ?, NOW() - INTERVAL '10 seconds')
        """,
                eventId,
                incidentId,
                "INCIDENT_CREATED",
                "worker-A"
        );

        // Worker B should reclaim the expired event.
        var claimedEvents = outboxClaimService.claimBatch("worker-B", 10);

        assertEquals(1, claimedEvents.size());

        var claimedEvent = claimedEvents.get(0);
        assertEquals(eventId, claimedEvent.getId());
        assertEquals(OutboxStatus.PROCESSING, claimedEvent.getStatus());
        assertEquals("worker-B", claimedEvent.getLockedBy());
        assertEquals(2, claimedEvent.getAttemptCount());
    }


    @Test
    void previousWorkerCannotCompleteReclaimedEvent() {
        UUID incidentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update("""
        INSERT INTO incidents (
            id, title, description, severity, status, source,
            created_at, updated_at, version
        )
        VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)
        """,
                incidentId,
                "Stale worker test",
                "Testing ownership after lease recovery",
                "HIGH",
                "OPEN",
                "integration-test"
        );

        jdbcTemplate.update("""
        INSERT INTO outbox_events (
            id, aggregate_id, event_type, payload, status,
            attempt_count, next_attempt_at, created_at,
            locked_by, locked_until
        )
        VALUES (?, ?, ?, '{}'::jsonb, 'PROCESSING',
                1, NOW(), NOW(), ?, NOW() - INTERVAL '10 seconds')
        """,
                eventId,
                incidentId,
                "INCIDENT_CREATED",
                "worker-A"
        );

        // Worker B reclaims the expired event.
        outboxClaimService.claimBatch("worker-B", 10);

        // Worker A must no longer be allowed to complete it.
        assertThrows(IllegalStateException.class, () ->
                outboxProcessingService.completeProcessing(
                        eventId,
                        "worker-A",
                        "Stale result",
                        "HIGH",
                        "Database connection pool usage is elevated."
                )
        );

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE id = ?",
                String.class,
                eventId
        );

        assertEquals("PROCESSING", status);

        Integer triageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM triage_results WHERE event_id = ?",
                Integer.class,
                eventId
        );

        assertEquals(0, triageCount);
    }


    @Test
    void failedAttemptSchedulesRetryInPostgres() {
        UUID incidentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String workerId = "retry-test-worker";

        jdbcTemplate.update("""
        INSERT INTO incidents (
            id, title, description, severity, status, source,
            created_at, updated_at, version
        )
        VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)
        """,
                incidentId,
                "Retry test",
                "Testing retry scheduling",
                "HIGH",
                "OPEN",
                "integration-test"
        );

        jdbcTemplate.update("""
        INSERT INTO outbox_events (
            id, aggregate_id, event_type, payload, status,
            attempt_count, next_attempt_at, created_at,
            locked_by, locked_until
        )
        VALUES (?, ?, ?, '{}'::jsonb, 'PROCESSING',
                1, NOW(), NOW(), ?, NOW() + INTERVAL '60 seconds')
        """,
                eventId,
                incidentId,
                "INCIDENT_CREATED",
                workerId
        );

        outboxRetryService.handleFailure(
                eventId,
                workerId,
                "Simulated processing failure"
        );

        var row = jdbcTemplate.queryForMap("""
        SELECT status, attempt_count, next_attempt_at,
               locked_by, locked_until, last_error
        FROM outbox_events
        WHERE id = ?
        """, eventId);

        assertEquals("PENDING", row.get("status"));
        assertEquals(1, row.get("attempt_count"));
        assertEquals(null, row.get("locked_by"));
        assertEquals(null, row.get("locked_until"));
        assertEquals("Simulated processing failure", row.get("last_error"));

        var nextAttemptAt = (java.sql.Timestamp) row.get("next_attempt_at");
        assertTrue(nextAttemptAt.toInstant().isAfter(java.time.Instant.now()));
    }


    @Test
    void fifthFailedAttemptMarksEventPermanentlyFailedInPostgres() {
        UUID incidentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String workerId = "final-attempt-worker";

        jdbcTemplate.update("""
        INSERT INTO incidents (
            id, title, description, severity, status, source,
            created_at, updated_at, version
        )
        VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)
        """,
                incidentId,
                "Permanent failure test",
                "Testing maximum retry attempts",
                "HIGH",
                "OPEN",
                "integration-test"
        );

        jdbcTemplate.update("""
        INSERT INTO outbox_events (
            id, aggregate_id, event_type, payload, status,
            attempt_count, next_attempt_at, created_at,
            locked_by, locked_until
        )
        VALUES (?, ?, ?, '{}'::jsonb, 'PROCESSING',
                5, NOW(), NOW(), ?, NOW() + INTERVAL '60 seconds')
        """,
                eventId,
                incidentId,
                "INCIDENT_CREATED",
                workerId
        );

        outboxRetryService.handleFailure(
                eventId,
                workerId,
                "Final attempt failed"
        );

        var row = jdbcTemplate.queryForMap("""
        SELECT status, attempt_count, locked_by,
               locked_until, last_error
        FROM outbox_events
        WHERE id = ?
        """, eventId);

        assertEquals("FAILED", row.get("status"));
        assertEquals(5, row.get("attempt_count"));
        assertEquals(null, row.get("locked_by"));
        assertEquals(null, row.get("locked_until"));
        assertEquals("Final attempt failed", row.get("last_error"));
    }


    @Test
    void completingAlreadyPublishedEventIsIdempotent() {
        UUID incidentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update("""
        INSERT INTO incidents (
            id, title, description, severity, status, source,
            created_at, updated_at, version
        )
        VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)
        """,
                incidentId,
                "Idempotency test",
                "Testing repeated completion",
                "HIGH",
                "OPEN",
                "integration-test"
        );

        jdbcTemplate.update("""
        INSERT INTO outbox_events (
            id, aggregate_id, event_type, payload, status,
            attempt_count, next_attempt_at, created_at,
            published_at
        )
        VALUES (?, ?, ?, '{}'::jsonb, 'PUBLISHED',
                1, NOW(), NOW(), NOW())
        """,
                eventId,
                incidentId,
                "INCIDENT_CREATED"
        );

        jdbcTemplate.update("""
        INSERT INTO triage_results (
            event_id, incident_id, triage_summary,
            recommended_severity, created_at, rationale
        )
        VALUES (?, ?, ?, ?, NOW(), ?)
        """,
                eventId,
                incidentId,
                "Previously saved result",
                "HIGH",
                "Existing result used to verify idempotent processing."
        );

        // Repeating completion should be harmless.
        assertDoesNotThrow(() ->
                outboxProcessingService.completeProcessing(
                        eventId,
                        "worker-retry",
                        "A different summary",
                        "CRITICAL",
                        "Database connection pool usage is elevated."
                )
        );

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM triage_results WHERE event_id = ?",
                Integer.class,
                eventId
        );

        assertEquals(1, count);

        String storedSummary = jdbcTemplate.queryForObject(
                "SELECT triage_summary FROM triage_results WHERE event_id = ?",
                String.class,
                eventId
        );

        assertEquals("Previously saved result", storedSummary);
    }

    @Test
    void replayResetsFailedEventForProcessing() {
        UUID incidentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update("""
        INSERT INTO incidents (
            id, title, description, severity, status, source,
            created_at, updated_at, version
        )
        VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)
        """,
                incidentId,
                "Replay test",
                "Testing failed event replay",
                "HIGH",
                "OPEN",
                "integration-test"
        );

        jdbcTemplate.update("""
        INSERT INTO outbox_events (
            id, aggregate_id, event_type, payload, status,
            attempt_count, next_attempt_at, created_at,
            locked_by, locked_until, last_error
        )
        VALUES (?, ?, ?, '{}'::jsonb, 'FAILED',
                5, NOW(), NOW(), NULL, NULL, ?)
        """,
                eventId,
                incidentId,
                "INCIDENT_CREATED",
                "Maximum attempts reached"
        );

        outboxReplayService.replay(eventId);

        var row = jdbcTemplate.queryForMap("""
        SELECT status, attempt_count, next_attempt_at,
               locked_by, locked_until, last_error
        FROM outbox_events
        WHERE id = ?
        """, eventId);

        assertEquals("PENDING", row.get("status"));
        assertEquals(0, row.get("attempt_count"));
        assertEquals(null, row.get("locked_by"));
        assertEquals(null, row.get("locked_until"));
        assertEquals(null, row.get("last_error"));

        var nextAttemptAt = (java.sql.Timestamp) row.get("next_attempt_at");
        assertFalse(nextAttemptAt.toInstant().isAfter(java.time.Instant.now()));
    }


    @Test
    void replayRejectsEventWithExistingTriageResult() {
        UUID incidentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update("""
        INSERT INTO incidents (
            id, title, description, severity, status, source,
            created_at, updated_at, version
        )
        VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)
        """,
                incidentId,
                "Replay conflict test",
                "Testing replay with an existing result",
                "HIGH",
                "OPEN",
                "integration-test"
        );

        jdbcTemplate.update("""
        INSERT INTO outbox_events (
            id, aggregate_id, event_type, payload, status,
            attempt_count, next_attempt_at, created_at, last_error
        )
        VALUES (?, ?, ?, '{}'::jsonb, 'FAILED',
                5, NOW(), NOW(), ?)
        """,
                eventId,
                incidentId,
                "INCIDENT_CREATED",
                "Previously failed"
        );

        jdbcTemplate.update("""
        INSERT INTO triage_results (
            event_id, incident_id, triage_summary,
            recommended_severity, created_at, rationale
        )
        VALUES (?, ?, ?, ?, NOW(), ?)
        """,
                eventId,
                incidentId,
                "Existing triage result",
                "HIGH",
                "Existing result used to verify idempotent processing."
        );

        assertThrows(IllegalStateException.class,
                () -> outboxReplayService.replay(eventId));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE id = ?",
                String.class,
                eventId
        );

        assertEquals("FAILED", status);
    }


    @Test
    void replayRejectsPendingEvent() {
        UUID incidentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update("""
        INSERT INTO incidents (
            id, title, description, severity, status, source,
            created_at, updated_at, version
        )
        VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)
        """,
                incidentId,
                "Invalid replay test",
                "Testing replay of a pending event",
                "HIGH",
                "OPEN",
                "integration-test"
        );

        jdbcTemplate.update("""
        INSERT INTO outbox_events (
            id, aggregate_id, event_type, payload, status,
            attempt_count, next_attempt_at, created_at
        )
        VALUES (?, ?, ?, '{}'::jsonb, 'PENDING',
                0, NOW(), NOW())
        """,
                eventId,
                incidentId,
                "INCIDENT_CREATED"
        );

        assertThrows(IllegalStateException.class,
                () -> outboxReplayService.replay(eventId));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE id = ?",
                String.class,
                eventId
        );

        assertEquals("PENDING", status);
    }
}
