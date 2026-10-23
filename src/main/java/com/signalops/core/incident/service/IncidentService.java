package com.signalops.core.incident.service;

import com.signalops.core.incident.enums.IncidentStatus;
import com.signalops.core.incident.model.IdempotencyRecord;
import com.signalops.core.incident.model.Incident;
import com.signalops.core.incident.model.OutboxEvent;
import com.signalops.core.incident.enums.Severity;
import com.signalops.core.incident.repository.IdempotencyRecordRepository;
import com.signalops.core.incident.repository.IncidentRepository;
import com.signalops.core.incident.repository.OutboxEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@Service
public class IncidentService {
    private final IncidentRepository incidentRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final TransactionTemplate transactionTemplate;
    private final OutboxEventRepository outboxEventRepository;

    public IncidentService(IncidentRepository incidentRepository, IdempotencyRecordRepository idempotencyRecordRepository, PlatformTransactionManager transactionManager, OutboxEventRepository outboxEventRepository) {
        this.incidentRepository = incidentRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public IncidentCreationResult createIncident(
            String title,
            String description,
            Severity severity,
            String source,
            Map<String, Object> metadata,
            String idempotencyKey,
            String requestHash
    ) {
        try {
            Incident created = transactionTemplate.execute(status -> {
                Incident incident = new Incident(
                        UUID.randomUUID(),
                        title,
                        description,
                        severity,
                        source,
                        metadata
                );
                Incident saved = incidentRepository.saveAndFlush(incident);
                Instant now = Instant.now();
                int claimed = idempotencyRecordRepository.claimKey(
                        idempotencyKey,
                        requestHash,
                        saved.getId(),
                        now,
                        now.plus(Duration.ofDays(7))
                );
                if (claimed == 0) {
                    status.setRollbackOnly();
                    throw new DuplicateKeyRaceException();
                }

                Map<String, Object> eventPayload = new HashMap<>();
                eventPayload.put("incidentId", saved.getId().toString());
                eventPayload.put("title", saved.getTitle());
                eventPayload.put("description", saved.getDescription());
                eventPayload.put("severity", saved.getSeverity().name());
                eventPayload.put("source", saved.getSource());
                eventPayload.put("createdAt", saved.getCreatedAt().toString());

                OutboxEvent event = new OutboxEvent(
                        UUID.randomUUID(),
                        saved.getId(),
                        "INCIDENT_CREATED",
                        eventPayload
                );

                outboxEventRepository.save(event);

                return saved;
            });
            if (created == null) {
                throw new IllegalStateException("Incident creation returned no result");
            }

            return new IncidentCreationResult(created, false);
        } catch (DuplicateKeyRaceException e) {
            return new IncidentCreationResult(
                    findExistingIncident(idempotencyKey, requestHash),
                    true
            );
        }
    }

    private Incident findExistingIncident(String key, String requestHash) {
        IdempotencyRecord record = idempotencyRecordRepository.findById(key)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency key conflict occurred, but no record was found"
                ));

        if (!record.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key was already used with a different request"
            );
        }

        return incidentRepository.findById(record.getIncidentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record references a missing incident"
                ));
    }

    public Incident getIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Incident not found: " + incidentId
                ));
    }

    public List<Incident> getAllIncidents() {
        return incidentRepository.findAll(
                org.springframework.data.domain.Sort
                        .by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        );
    }

    @Transactional
    public Incident updateIncidentStatus(
            UUID incidentId,
            IncidentStatus newStatus
    ) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Incident not found: " + incidentId
                ));

        try {
            incident.changeStatus(newStatus);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );
        }

        return incident;
    }

    @Transactional(readOnly = true)
    public List<Incident> getIncidentsByStatus(IncidentStatus status) {
        return incidentRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    private static class DuplicateKeyRaceException extends RuntimeException {}
    public record IncidentCreationResult(Incident incident, boolean replayed) {}
}
