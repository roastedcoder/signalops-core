
package com.signalops.core.incident;

import com.signalops.core.incident.model.OutboxEvent;
import com.signalops.core.incident.dto.AiTriageDecision;
import com.signalops.core.incident.service.AiTriageService;
import com.signalops.core.incident.service.IncidentTriageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentTriageServiceTest {

    private AiTriageService aiTriageService;
    private IncidentTriageService incidentTriageService;

    @BeforeEach
    void setUp() {
        aiTriageService = mock(AiTriageService.class);
        incidentTriageService =
                new IncidentTriageService(aiTriageService);
    }

    @Test
    void extractsPayloadCallsAiAndMapsDecision() {
        OutboxEvent event = createEvent(Map.of(
                "title", "Checkout latency spike",
                "description", "p95 latency increased to 2400ms",
                "severity", "HIGH",
                "source", "checkout-service"
        ));

        when(aiTriageService.triage(
                "Checkout latency spike",
                "p95 latency increased to 2400ms",
                "HIGH",
                "checkout-service"
        )).thenReturn(new AiTriageDecision(
                "Checkout latency is elevated",
                "HIGH",
                "Significant latency increase"
        ));

        IncidentTriageService.TriageDecision result =
                incidentTriageService.triage(event);

        assertEquals(
                "Checkout latency is elevated",
                result.summary()
        );
        assertEquals("HIGH", result.recommendedSeverity());

        verify(aiTriageService).triage(
                "Checkout latency spike",
                "p95 latency increased to 2400ms",
                "HIGH",
                "checkout-service"
        );
        verifyNoMoreInteractions(aiTriageService);
    }

    @Test
    void rejectsMissingTitleBeforeCallingAi() {
        OutboxEvent event = createEvent(Map.of(
                "severity", "HIGH"
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> incidentTriageService.triage(event)
        );

        verifyNoInteractions(aiTriageService);
    }

    @Test
    void rejectsInvalidSeverityBeforeCallingAi() {
        OutboxEvent event = createEvent(Map.of(
                "title", "Checkout latency spike",
                "severity", "URGENT"
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> incidentTriageService.triage(event)
        );

        verifyNoInteractions(aiTriageService);
    }

    @Test
    void usesDefaultsWhenOptionalFieldsAreMissing() {
        OutboxEvent event = createEvent(Map.of(
                "title", "Checkout latency spike",
                "severity", "HIGH"
        ));

        when(aiTriageService.triage(
                "Checkout latency spike",
                "Not provided",
                "HIGH",
                "Not provided"
        )).thenReturn(new AiTriageDecision(
                "Checkout latency is elevated",
                "MEDIUM",
                "Impact needs further investigation"
        ));

        IncidentTriageService.TriageDecision result =
                incidentTriageService.triage(event);

        assertEquals("MEDIUM", result.recommendedSeverity());

        verify(aiTriageService).triage(
                "Checkout latency spike",
                "Not provided",
                "HIGH",
                "Not provided"
        );
    }

    private OutboxEvent createEvent(Map<String, Object> payload) {
        return new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "INCIDENT_CREATED",
                payload
        );
    }
}
