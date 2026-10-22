
package com.signalops.core.incident.service;

import com.signalops.core.incident.enums.OutboxStatus;
import com.signalops.core.incident.model.OutboxEvent;
import com.signalops.core.incident.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxRetryService {

    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository outboxEventRepository;

    public OutboxRetryService(
            OutboxEventRepository outboxEventRepository
    ) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public boolean handleFailure(
            UUID eventId,
            String workerId,
            String errorMessage
    ) {
        OutboxEvent event = outboxEventRepository
                .findByIdForUpdate(eventId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Outbox event not found: " + eventId
                        ));

        Instant now = Instant.now();
        if (event.getStatus() != OutboxStatus.PROCESSING
                || !workerId.equals(event.getLockedBy())
                || event.getLockedUntil() == null
                || !event.getLockedUntil().isAfter(now)) {
            throw new IllegalStateException(
                    "Worker does not own a valid processing lease: " + eventId
            );
        }

        if (event.getAttemptCount() >= MAX_ATTEMPTS) {
            event.markPermanentlyFailed(errorMessage);
            return false;
        }

        long delaySeconds =
                1L << event.getAttemptCount();

        Instant nextAttemptAt =
                Instant.now().plusSeconds(delaySeconds);

        event.markFailed(nextAttemptAt, errorMessage);
        return true;
    }
}
