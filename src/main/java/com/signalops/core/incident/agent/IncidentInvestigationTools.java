package com.signalops.core.incident.agent;

import com.signalops.core.incident.model.Incident;
import com.signalops.core.incident.service.IncidentService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only tools available to the SignalOps incident investigation agent.
 *
 * <p>Tool methods retrieve facts from SignalOps or calculate deterministic
 * values. They do not modify incidents or execute remediation.</p>
 */
@Component
public class IncidentInvestigationTools {

    private final IncidentService incidentService;
    private final long overdueThresholdMinutes;

    public IncidentInvestigationTools(
            IncidentService incidentService,
            @Value("${signalops.agent.overdue-threshold-minutes:30}")
            long overdueThresholdMinutes
    ) {
        if (overdueThresholdMinutes <= 0) {
            throw new IllegalArgumentException(
                    "The overdue threshold must be greater than zero");
        }

        this.incidentService = incidentService;
        this.overdueThresholdMinutes = overdueThresholdMinutes;
    }

    @Tool(description = """
            Retrieve the current details of a SignalOps incident by its UUID.
            Use this tool before investigating an incident. It returns stored
            facts only; it does not infer a root cause.
            """)
    public IncidentDetails getIncidentDetails(
            @ToolParam(description = "UUID of the SignalOps incident")
            UUID incidentId
    ) {
        Incident incident = incidentService.getIncident(incidentId);

        return new IncidentDetails(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getSeverity().name(),
                incident.getStatus().name(),
                incident.getSource(),
                incident.getMetadata(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }

    @Tool(description = """
            Calculate the age of a SignalOps incident from its creation time.
            Also report whether it is unresolved and whether it has reached
            the configured overdue threshold. This calculation uses database
            incident fields only; it does not indicate application-log evidence.
            """)
    public IncidentAgeAnalysis analyzeIncidentAge(
            @ToolParam(description = "UUID of the SignalOps incident")
            UUID incidentId
    ) {
        Incident incident = incidentService.getIncident(incidentId);

        Instant createdAt = incident.getCreatedAt();
        Instant now = Instant.now();
        long ageMinutes = Math.max(
                0L,
                Duration.between(createdAt, now).toMinutes()
        );
        boolean unresolved = !"RESOLVED".equals(incident.getStatus().name());

        return new IncidentAgeAnalysis(
                incident.getId(),
                createdAt,
                incident.getStatus().name(),
                ageMinutes,
                unresolved,
                unresolved && ageMinutes >= overdueThresholdMinutes,
                overdueThresholdMinutes
        );
    }

    public record IncidentDetails(
            UUID id,
            String title,
            String description,
            String severity,
            String status,
            String source,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record IncidentAgeAnalysis(
            UUID incidentId,
            Instant createdAt,
            String currentStatus,
            long ageMinutes,
            boolean unresolved,
            boolean overdue,
            long overdueThresholdMinutes
    ) {
    }
}
