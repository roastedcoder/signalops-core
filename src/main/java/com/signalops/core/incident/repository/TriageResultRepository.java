
package com.signalops.core.incident.repository;

import com.signalops.core.incident.model.TriageResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TriageResultRepository
        extends JpaRepository<TriageResult, UUID> {

    Optional<TriageResult> findFirstByIncidentIdOrderByCreatedAtDesc(
            UUID incidentId
    );
}
