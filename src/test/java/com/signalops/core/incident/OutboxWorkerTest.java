
package com.signalops.core.incident;

import com.signalops.core.incident.model.OutboxEvent;
import com.signalops.core.incident.service.IncidentTriageService;
import com.signalops.core.incident.service.OutboxClaimService;
import com.signalops.core.incident.service.OutboxProcessingService;
import com.signalops.core.incident.service.OutboxRetryService;
import com.signalops.core.incident.worker.OutboxMetrics;
import com.signalops.core.incident.worker.OutboxWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxWorkerTest {

    private OutboxClaimService outboxClaimService;
    private OutboxProcessingService outboxProcessingService;
    private IncidentTriageService incidentTriageService;
    private OutboxRetryService outboxRetryService;
    private OutboxMetrics outboxMetrics;

    private OutboxWorker outboxWorker;

    @BeforeEach
    void setUp() {
        outboxClaimService = mock(OutboxClaimService.class);
        outboxProcessingService = mock(OutboxProcessingService.class);
        incidentTriageService = mock(IncidentTriageService.class);
        outboxRetryService = mock(OutboxRetryService.class);
        outboxMetrics = mock(OutboxMetrics.class);

        outboxWorker = new OutboxWorker(
                outboxClaimService,
                outboxProcessingService,
                incidentTriageService,
                outboxRetryService,
                outboxMetrics
        );
    }

    @Test
    void retriesWhenIncidentTriageFails() {
        OutboxEvent event = createEvent();

        when(outboxClaimService.claimBatch(anyString(), eq(1)))
                .thenReturn(List.of(event), List.of());

        when(incidentTriageService.triage(event))
                .thenThrow(new RuntimeException("AI service unavailable"));

        outboxWorker.pollOutbox();

        verify(outboxClaimService, times(2))
                .claimBatch(anyString(), eq(1));

        verify(incidentTriageService, times(1)).triage(event);

        verify(outboxProcessingService, never()).completeProcessing(
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );

        verify(outboxMetrics).recordFailure();
        verify(outboxMetrics, never()).recordProcessed();

        verify(outboxRetryService).handleFailure(
                eq(event.getId()),
                anyString(),
                eq("AI service unavailable")
        );
    }

    @Test
    void completesProcessingWhenTriageSucceeds() {
        OutboxEvent event = createEvent();

        when(outboxClaimService.claimBatch(anyString(), eq(1)))
                .thenReturn(List.of(event), List.of());

        when(incidentTriageService.triage(event))
                .thenReturn(
                        new IncidentTriageService.TriageDecision(
                                "Checkout latency is elevated",
                                "HIGH",
                                "Checkout latency is elevated based on the supplied incident details."
                        )
                );

        outboxWorker.pollOutbox();

        verify(outboxClaimService, times(2))
                .claimBatch(anyString(), eq(1));

        verify(incidentTriageService, times(1)).triage(event);

        verify(outboxProcessingService).completeProcessing(
                eq(event.getId()),
                anyString(),
                eq("Checkout latency is elevated"),
                eq("HIGH"),
                eq("Checkout latency is elevated based on the supplied incident details.")
        );

        verify(outboxMetrics).recordProcessed();
        verify(outboxMetrics, never()).recordFailure();
        verifyNoInteractions(outboxRetryService);
    }

    @Test
    void continuesProcessingOtherEventsAfterOneFails() {
        OutboxEvent failedEvent = createEvent();
        OutboxEvent successfulEvent = createEvent();

        when(outboxClaimService.claimBatch(anyString(), eq(1)))
                .thenReturn(
                        List.of(failedEvent),
                        List.of(successfulEvent),
                        List.of()
                );

        when(incidentTriageService.triage(failedEvent))
                .thenThrow(new RuntimeException("AI timeout"));

        when(incidentTriageService.triage(successfulEvent))
                .thenReturn(
                        new IncidentTriageService.TriageDecision(
                                "Database connection usage is high",
                                "MEDIUM",
                                "The supplied incident details indicate high database connection usage."
                        )
                );

        outboxWorker.pollOutbox();

        verify(outboxClaimService, times(3))
                .claimBatch(anyString(), eq(1));

        verify(incidentTriageService).triage(failedEvent);
        verify(incidentTriageService).triage(successfulEvent);

        verify(outboxRetryService).handleFailure(
                eq(failedEvent.getId()),
                anyString(),
                eq("AI timeout")
        );

        verify(outboxProcessingService).completeProcessing(
                eq(successfulEvent.getId()),
                anyString(),
                eq("Database connection usage is high"),
                eq("MEDIUM"),
                eq("The supplied incident details indicate high database connection usage.")
        );

        verify(outboxMetrics).recordFailure();
        verify(outboxMetrics).recordProcessed();
    }

    @Test
    void stopsPollingWhenNoEventsAreAvailable() {
        when(outboxClaimService.claimBatch(anyString(), eq(1)))
                .thenReturn(List.of());

        outboxWorker.pollOutbox();

        verify(outboxClaimService, times(1))
                .claimBatch(anyString(), eq(1));

        verifyNoInteractions(incidentTriageService);
        verifyNoInteractions(outboxProcessingService);
        verifyNoInteractions(outboxRetryService);
        verifyNoInteractions(outboxMetrics);
    }

    private OutboxEvent createEvent() {
        return new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "INCIDENT_CREATED",
                Map.of(
                        "title", "Checkout latency spike",
                        "severity", "HIGH",
                        "description", "p95 latency increased",
                        "source", "checkout-service"
                )
        );
    }

    @Test
    void doesNotRecordRetryWhenEventPermanentlyFails() {
        OutboxEvent event = createEvent();

        when(outboxClaimService.claimBatch(anyString(), eq(1)))
                .thenReturn(List.of(event), List.of());

        when(incidentTriageService.triage(event))
                .thenThrow(new RuntimeException("AI service unavailable"));

        when(outboxRetryService.handleFailure(
                eq(event.getId()),
                anyString(),
                eq("AI service unavailable")
        )).thenReturn(false);

        outboxWorker.pollOutbox();

        verify(outboxMetrics).recordFailure();
        verify(outboxMetrics, never()).recordRetryScheduled();
        verify(outboxMetrics).recordProcessingDuration(anyLong());
    }
}
