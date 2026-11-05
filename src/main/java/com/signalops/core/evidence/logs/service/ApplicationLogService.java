package com.signalops.core.evidence.logs.service;

import com.signalops.core.evidence.logs.model.ApplicationLog;
import com.signalops.core.evidence.logs.repository.ApplicationLogRepository;
import com.signalops.core.incident.model.Incident;
import com.signalops.core.incident.service.IncidentService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores application log evidence and performs bounded, read-only searches
 * around an incident's creation time.
 */
@Service
public class ApplicationLogService {

    private static final Duration WINDOW_BEFORE_INCIDENT = Duration.ofMinutes(15);
    private static final Duration WINDOW_AFTER_INCIDENT = Duration.ofMinutes(15);
    private static final int MAX_RESULTS = 50;

    private static final List<String> ALLOWED_LEVELS =
            List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    private final ApplicationLogRepository applicationLogRepository;
    private final IncidentService incidentService;

    public ApplicationLogService(
            ApplicationLogRepository applicationLogRepository,
            IncidentService incidentService
    ) {
        this.applicationLogRepository = applicationLogRepository;
        this.incidentService = incidentService;
    }

    @Transactional
    public ApplicationLog ingest(
            String serviceName,
            Instant occurredAt,
            String level,
            String message,
            UUID incidentId,
            Map<String, Object> attributes
    ) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Service name must not be blank");
        }
        if (serviceName.length() > 100) {
            throw new IllegalArgumentException("Service name must not exceed 100 characters");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Log timestamp must not be null");
        }
        if (level == null || !ALLOWED_LEVELS.contains(level)) {
            throw new IllegalArgumentException("Unsupported log level: " + level);
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Log message must not be blank");
        }
        if (incidentId != null) {
            incidentService.getIncident(incidentId);
        }

        ApplicationLog applicationLog = new ApplicationLog(
                UUID.randomUUID(),
                serviceName,
                occurredAt,
                level,
                message,
                incidentId,
                attributes
        );

        return applicationLogRepository.save(applicationLog);
    }

    @Transactional(readOnly = true)
    public List<LogEvidence> searchForIncident(UUID incidentId) {
        Incident incident = incidentService.getIncident(incidentId);

        Instant from = incident.getCreatedAt().minus(WINDOW_BEFORE_INCIDENT);
        Instant to = incident.getCreatedAt().plus(WINDOW_AFTER_INCIDENT);

        return applicationLogRepository
                .findByServiceNameAndOccurredAtBetweenOrderByOccurredAtDesc(
                        incident.getSource(),
                        from,
                        to,
                        PageRequest.of(0, MAX_RESULTS)
                )
                .stream()
                .map(log -> new LogEvidence(
                        log.getId(),
                        log.getServiceName(),
                        log.getOccurredAt(),
                        log.getLevel(),
                        log.getMessage(),
                        log.getAttributes()
                ))
                .toList();
    }

    public record LogEvidence(
            UUID id,
            String serviceName,
            Instant occurredAt,
            String level,
            String message,
            Map<String, Object> attributes
    ) {
    }
}
