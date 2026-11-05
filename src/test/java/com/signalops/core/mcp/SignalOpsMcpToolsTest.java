package com.signalops.core.mcp;

import com.signalops.core.evidence.logs.service.ApplicationLogService;
import com.signalops.core.incident.agent.IncidentInvestigationAgent;
import com.signalops.core.incident.enums.IncidentStatus;
import com.signalops.core.incident.enums.Severity;
import com.signalops.core.incident.model.Incident;
import com.signalops.core.incident.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignalOpsMcpToolsTest {

    private IncidentService incidentService;
    private ApplicationLogService applicationLogService;
    private IncidentInvestigationAgent investigationAgent;
    private SignalOpsMcpTools tools;

    @BeforeEach
    void setUp() {
        incidentService = mock(IncidentService.class);
        applicationLogService = mock(ApplicationLogService.class);
        investigationAgent = mock(IncidentInvestigationAgent.class);
        tools = new SignalOpsMcpTools(
                incidentService, applicationLogService, investigationAgent
        );
    }

    @Test
    void listIncidentsReturnsSummariesAndDelegatesToService() {
        Incident incident = incident();
        when(incidentService.getAllIncidents()).thenReturn(List.of(incident));

        var result = tools.listIncidents(null);

        assertEquals(1, result.size());
        assertEquals(incident.getId(), result.getFirst().id());
        assertEquals("checkout", result.getFirst().source());
        verify(incidentService).getAllIncidents();
    }

    @Test
    void listIncidentsAppliesStatusFilter() {
        when(incidentService.getIncidentsByStatus(IncidentStatus.OPEN))
                .thenReturn(List.of(incident()));

        var result = tools.listIncidents("open");

        assertEquals(1, result.size());
        verify(incidentService).getIncidentsByStatus(IncidentStatus.OPEN);
    }

    @Test
    void listIncidentsRejectsUnknownStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> tools.listIncidents("BROKEN"));
        verifyNoInteractions(incidentService);
    }

    @Test
    void getIncidentDelegatesAndMapsStoredFields() {
        Incident incident = incident();
        when(incidentService.getIncident(incident.getId())).thenReturn(incident);

        var result = tools.getIncident(incident.getId().toString());

        assertEquals(incident.getId(), result.id());
        assertEquals(incident.getTitle(), result.title());
        assertEquals(incident.getMetadata(), result.metadata());
    }

    @Test
    void getIncidentRejectsMalformedUuid() {
        assertThrows(IllegalArgumentException.class,
                () -> tools.getIncident("not-a-uuid"));
        verifyNoInteractions(incidentService);
    }

    @Test
    void getIncidentLogsDelegatesToBoundedLogSearch() {
        UUID incidentId = UUID.randomUUID();
        when(applicationLogService.searchForIncident(incidentId)).thenReturn(List.of());

        var result = tools.getIncidentLogs(incidentId.toString());

        assertEquals(incidentId, result.incidentId());
        assertTrue(result.logs().isEmpty());
        verify(applicationLogService).searchForIncident(incidentId);
    }

    @Test
    void investigateIncidentDelegatesToExistingAgent() {
        UUID incidentId = UUID.randomUUID();
        var report = new IncidentInvestigationAgent.InvestigationReport(
                "Checkout errors observed",
                List.of("One HTTP 503 log was found"),
                List.of(),
                List.of("No metrics are available"),
                "MEDIUM",
                List.of("Inspect checkout dependency health")
        );
        when(investigationAgent.investigate(incidentId)).thenReturn(report);

        assertSame(report, tools.investigateIncident(incidentId.toString()));
        verify(investigationAgent).investigate(incidentId);
    }

    private Incident incident() {
        return new Incident(
                UUID.randomUUID(),
                "Checkout latency",
                "Latency increased",
                Severity.HIGH,
                "checkout",
                Map.of("region", "us-east")
        );
    }
}
