package com.signalops.core.evidence.logs.repository;

import com.signalops.core.evidence.logs.model.ApplicationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ApplicationLogRepository
        extends JpaRepository<ApplicationLog, UUID> {

    List<ApplicationLog> findByServiceNameAndOccurredAtBetweenOrderByOccurredAtDesc(
            String serviceName,
            Instant from,
            Instant to,
            Pageable pageable
    );
}
