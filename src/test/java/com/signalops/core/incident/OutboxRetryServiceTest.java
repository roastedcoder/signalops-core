
package com.signalops.core.incident;

import com.signalops.core.incident.enums.OutboxStatus;
import com.signalops.core.incident.model.OutboxEvent;
import com.signalops.core.incident.repository.OutboxEventRepository;
import com.signalops.core.incident.service.OutboxRetryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRetryServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxRetryService outboxRetryService;

    @Test
    void fifthFailedAttemptMarksEventPermanentlyFailed() {
        UUID eventId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        String workerId = "test-worker";

        OutboxEvent event = new OutboxEvent(
                eventId,
                incidentId,
                "INCIDENT_CREATED",
                java.util.Map.of(
                        "title", "Test incident",
                        "severity", "HIGH"
                )
        );

        event.markProcessing(workerId, Instant.now().plusSeconds(60));

        // Simulate this being the fifth claimed attempt.
        for (int i = 1; i < 5; i++) {
            event.markProcessing(workerId, Instant.now().plusSeconds(60));
        }

        when(outboxEventRepository.findByIdForUpdate(eventId))
                .thenReturn(Optional.of(event));

        outboxRetryService.handleFailure(
                eventId,
                workerId,
                "Simulated processing failure"
        );

        assertEquals(OutboxStatus.FAILED, event.getStatus());
        verify(outboxEventRepository).findByIdForUpdate(eventId);
    }


    @Test
    void failureBeforeMaxAttemptsSchedulesRetry() {
        UUID eventId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        String workerId = "test-worker";

        OutboxEvent event = new OutboxEvent(
                eventId,
                incidentId,
                "INCIDENT_CREATED",
                java.util.Map.of(
                        "title", "Test incident",
                        "severity", "HIGH"
                )
        );

        // First claimed attempt.
        event.markProcessing(workerId, Instant.now().plusSeconds(60));

        when(outboxEventRepository.findByIdForUpdate(eventId))
                .thenReturn(Optional.of(event));

        Instant beforeFailure = Instant.now();

        outboxRetryService.handleFailure(
                eventId,
                workerId,
                "Simulated temporary failure"
        );

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        org.junit.jupiter.api.Assertions.assertTrue(
                event.getNextAttemptAt().isAfter(beforeFailure)
        );
        verify(outboxEventRepository).findByIdForUpdate(eventId);
    }


    @Test
    void rejectsFailureUpdateFromWrongWorker() {
        UUID eventId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();

        OutboxEvent event = new OutboxEvent(
                eventId,
                incidentId,
                "INCIDENT_CREATED",
                java.util.Map.of(
                        "title", "Test incident",
                        "severity", "HIGH"
                )
        );

        event.markProcessing(
                "worker-A",
                Instant.now().plusSeconds(60)
        );

        when(outboxEventRepository.findByIdForUpdate(eventId))
                .thenReturn(Optional.of(event));

        assertThrows(
                IllegalStateException.class,
                () -> outboxRetryService.handleFailure(
                        eventId,
                        "worker-B",
                        "Simulated failure"
                )
        );

        assertEquals(OutboxStatus.PROCESSING, event.getStatus());
        assertEquals("worker-A", event.getLockedBy());
    }


    @Test
    void rejectsFailureUpdateWhenLeaseHasExpired() {
        UUID eventId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        String workerId = "worker-A";

        OutboxEvent event = new OutboxEvent(
                eventId,
                incidentId,
                "INCIDENT_CREATED",
                java.util.Map.of(
                        "title", "Test incident",
                        "severity", "HIGH"
                )
        );

        event.markProcessing(
                workerId,
                Instant.now().minusSeconds(10)
        );

        when(outboxEventRepository.findByIdForUpdate(eventId))
                .thenReturn(Optional.of(event));

        assertThrows(
                IllegalStateException.class,
                () -> outboxRetryService.handleFailure(
                        eventId,
                        workerId,
                        "Simulated failure"
                )
        );

        assertEquals(OutboxStatus.PROCESSING, event.getStatus());
        assertEquals(workerId, event.getLockedBy());
    }


}
