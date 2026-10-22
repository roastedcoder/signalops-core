
package com.signalops.core.incident.worker;

import com.signalops.core.incident.model.OutboxEvent;
import com.signalops.core.incident.service.IncidentTriageService;
import com.signalops.core.incident.service.OutboxClaimService;
import com.signalops.core.incident.service.OutboxProcessingService;
import com.signalops.core.incident.service.OutboxRetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class OutboxWorker {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxWorker.class);

    private static final int MAX_EVENTS_PER_POLL = 10;

    private final OutboxClaimService outboxClaimService;
    private final OutboxProcessingService outboxProcessingService;
    private final IncidentTriageService incidentTriageService;
    private final OutboxRetryService outboxRetryService;
    private final OutboxMetrics outboxMetrics;

    private final String workerId = UUID.randomUUID().toString();

    public OutboxWorker(
            OutboxClaimService outboxClaimService,
            OutboxProcessingService outboxProcessingService,
            IncidentTriageService incidentTriageService,
            OutboxRetryService outboxRetryService,
            OutboxMetrics outboxMetrics
    ) {
        this.outboxClaimService = outboxClaimService;
        this.outboxProcessingService = outboxProcessingService;
        this.incidentTriageService = incidentTriageService;
        this.outboxRetryService = outboxRetryService;
        this.outboxMetrics = outboxMetrics;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollOutbox() {
        log.info("Outbox worker polling. workerId={}", workerId);

        int processedCount = 0;

        while (processedCount < MAX_EVENTS_PER_POLL) {
            List<OutboxEvent> events =
                    outboxClaimService.claimBatch(workerId, 1);

            if (events.isEmpty()) {
                break;
            }

            OutboxEvent event = events.get(0);
            processEvent(event);
            processedCount++;
        }

        log.info(
                "Outbox polling cycle finished. workerId={}, claimed={}",
                workerId,
                processedCount
        );
    }

    private void processEvent(OutboxEvent event) {
        long startNanos = System.nanoTime();
        try {
            IncidentTriageService.TriageDecision decision =
                    incidentTriageService.triage(event);

            outboxProcessingService.completeProcessing(
                    event.getId(),
                    workerId,
                    decision.summary(),
                    decision.recommendedSeverity(),
                    decision.rationale()
            );

            outboxMetrics.recordProcessed();

            log.info(
                    "Processed event id={}, severity={}",
                    event.getId(),
                    decision.recommendedSeverity()
            );
        } catch (Exception e) {
            log.error(
                    "Failed to process event id={}, error={}",
                    event.getId(),
                    e.getMessage(),
                    e
            );

            outboxMetrics.recordFailure();

            try {
                outboxRetryService.handleFailure(
                        event.getId(),
                        workerId,
                        e.getMessage() == null
                                ? e.getClass().getSimpleName()
                                : e.getMessage()
                );
            } catch (Exception retryException) {
                log.error(
                        "Failed to update retry state for event id={}",
                        event.getId(),
                        retryException
                );
            }
        } finally {
            outboxMetrics.recordProcessingDuration(
                    System.nanoTime() - startNanos
            );
        }
    }
}
