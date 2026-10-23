package com.signalops.core.incident.repository;

import com.signalops.core.incident.enums.IncidentStatus;
import com.signalops.core.incident.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByStatusOrderByCreatedAtDesc(IncidentStatus status);
}
