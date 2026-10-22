
package com.signalops.core.incident.service;

import com.signalops.core.incident.model.TriageResult;
import com.signalops.core.incident.repository.TriageResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class TriageResultService {

    private final TriageResultRepository triageResultRepository;

    public TriageResultService(
            TriageResultRepository triageResultRepository
    ) {
        this.triageResultRepository = triageResultRepository;
    }

    public TriageResult getLatestForIncident(UUID incidentId) {
        return triageResultRepository
                .findFirstByIncidentIdOrderByCreatedAtDesc(incidentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No triage result found for incident: " + incidentId
                ));
    }
}
