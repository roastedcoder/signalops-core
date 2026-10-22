
package com.signalops.core.incident.service;

import com.signalops.core.incident.enums.OutboxStatus;
import com.signalops.core.incident.model.OutboxEvent;
import com.signalops.core.incident.model.TriageResult;
import com.signalops.core.incident.repository.OutboxEventRepository;
import com.signalops.core.incident.repository.TriageResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxProcessingService {

    private final OutboxEventRepository outboxEventRepository;
    private final TriageResultRepository triageResultRepository;

    public OutboxProcessingService(
            OutboxEventRepository outboxEventRepository,
            TriageResultRepository triageResultRepository
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.triageResultRepository = triageResultRepository;
    }


    @Transactional
    public void completeProcessing(
            UUID eventId,
            String workerId,
            String triageSummary,
            String recommendedSeverity,
            String rationale
    ) {
        OutboxEvent event = outboxEventRepository
                .findByIdForUpdate(eventId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Outbox event not found: " + eventId
                        ));

        // A previously committed completion is already successful.
        if (event.getStatus() == OutboxStatus.PUBLISHED) {
            if (triageResultRepository.existsById(eventId)) {
                return;
            }

            throw new IllegalStateException(
                    "Published event has no triage result: " + eventId
            );
        }

        if (event.getStatus() != OutboxStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Event is not PROCESSING: " + eventId
            );
        }

        if (!workerId.equals(event.getLockedBy())) {
            throw new IllegalStateException(
                    "Worker does not own event: " + eventId
            );
        }

        if (event.getLockedUntil() == null
                || !event.getLockedUntil().isAfter(Instant.now())) {
            throw new IllegalStateException(
                    "Worker lease has expired: " + eventId
            );
        }

        if (triageResultRepository.existsById(eventId)) {
            throw new IllegalStateException(
                    "Triage result exists for an unpublished event: " + eventId
            );
        }

        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException(
                    "Triage rationale must not be blank"
            );
        }

        TriageResult result = new TriageResult(
                eventId,
                event.getAggregateId(),
                triageSummary,
                recommendedSeverity,
                rationale
        );

        triageResultRepository.save(result);
        event.markPublished(Instant.now());
    }

}
