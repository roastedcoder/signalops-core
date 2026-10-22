
package com.signalops.core.incident.service;

import com.signalops.core.incident.model.OutboxEvent;
import com.signalops.core.incident.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OutboxClaimService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxClaimService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public List<OutboxEvent> claimBatch(String workerId, int batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException(
                    "Batch size must be between 1 and 100"
            );
        }

        Instant now = Instant.now();

        List<OutboxEvent> events =
                outboxEventRepository.claimPendingEvents(now, batchSize);

        for (OutboxEvent event : events) {
            event.markProcessing(workerId, now.plusSeconds(30));
        }

        return events;
    }
}
