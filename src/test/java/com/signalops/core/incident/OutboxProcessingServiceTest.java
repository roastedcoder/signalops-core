
package com.signalops.core.incident;

import com.signalops.core.incident.model.OutboxEvent;
import com.signalops.core.incident.model.TriageResult;
import com.signalops.core.incident.repository.OutboxEventRepository;
import com.signalops.core.incident.repository.TriageResultRepository;
import com.signalops.core.incident.service.OutboxProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxProcessingServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TriageResultRepository triageResultRepository;

    @InjectMocks
    private OutboxProcessingService outboxProcessingService;

    private UUID eventId;
    private OutboxEvent event;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();

        event = new OutboxEvent(
                eventId,
                UUID.randomUUID(),
                "INCIDENT_CREATED",
                new HashMap<>()
        );

        // Worker A claims the event.
        event.markProcessing("worker-A", Instant.now().plusSeconds(30));

        // Its lease expires, and Worker B reclaims it.
        event.markProcessing("worker-B", Instant.now().plusSeconds(60));

        when(outboxEventRepository.findByIdForUpdate(eventId))
                .thenReturn(Optional.of(event));
    }

    @Test
    void rejectsCompletionFromOldWorkerAfterEventIsReclaimed() {
        assertThrows(
                IllegalStateException.class,
                () -> outboxProcessingService.completeProcessing(
                        eventId,
                        "worker-A",
                        "Test summary",
                        "HIGH",
                        null
                )
        );

        // The old worker must not write a triage result.
        verify(triageResultRepository, never()).save(
                org.mockito.ArgumentMatchers.any(TriageResult.class)
        );
    }
}
