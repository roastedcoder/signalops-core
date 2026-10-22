
package com.signalops.core.incident.service;

import com.signalops.core.incident.enums.OutboxStatus;
import com.signalops.core.incident.model.OutboxEvent;
import com.signalops.core.incident.repository.OutboxEventRepository;
import com.signalops.core.incident.repository.TriageResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxReplayService {

    private final OutboxEventRepository outboxEventRepository;
    private final TriageResultRepository triageResultRepository;

    public OutboxReplayService(
            OutboxEventRepository outboxEventRepository,
            TriageResultRepository triageResultRepository
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.triageResultRepository = triageResultRepository;
    }

    @Transactional
    public void replay(UUID eventId) {
        OutboxEvent event = outboxEventRepository
                .findByIdForUpdate(eventId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Outbox event not found: " + eventId
                        ));

        if (event.getStatus() != OutboxStatus.FAILED) {
            throw new IllegalStateException(
                    "Only FAILED events can be replayed: " + eventId
            );
        }

        if (triageResultRepository.existsById(eventId)) {
            throw new IllegalStateException(
                    "Cannot replay event with an existing triage result: "
                            + eventId
            );
        }

        event.replay(Instant.now());
    }
}
