
package com.signalops.core.incident.worker;

import com.signalops.core.incident.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class OutboxMetrics {

    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Counter retryCounter;
    private final Timer processingTimer;

    public OutboxMetrics(
            MeterRegistry registry,
            OutboxEventRepository outboxEventRepository
    ) {
        this.processedCounter = Counter.builder("signalops.outbox.processed")
                .description("Number of successfully processed outbox events")
                .register(registry);

        this.failedCounter = Counter.builder("signalops.outbox.failed")
                .description("Number of outbox processing failures")
                .register(registry);

        this.retryCounter = Counter.builder("signalops.outbox.retries")
                .description("Number of outbox retry attempts scheduled")
                .register(registry);

        this.processingTimer = Timer.builder(
                        "signalops.outbox.processing.duration"
                )
                .description("Time spent processing an outbox event")
                .register(registry);

        Gauge.builder(
                        "signalops.outbox.pending",
                        outboxEventRepository,
                        repository -> repository.countPendingEvents()
                )
                .description("Number of outbox events waiting to be processed")
                .register(registry);

        Gauge.builder(
                        "signalops.outbox.processing",
                        outboxEventRepository,
                        repository -> repository.countProcessingEvents()
                )
                .description("Number of outbox events currently being processed")
                .register(registry);

        Gauge.builder(
                        "signalops.outbox.permanently_failed",
                        outboxEventRepository,
                        repository -> repository.countFailedEvents()
                )
                .description("Number of outbox events that permanently failed")
                .register(registry);
    }

    public void recordProcessed() {
        processedCounter.increment();
    }

    public void recordFailure() {
        failedCounter.increment();
    }

    public void recordRetryScheduled() {
        retryCounter.increment();
    }

    public void recordProcessingDuration(long durationNanos) {
        processingTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
