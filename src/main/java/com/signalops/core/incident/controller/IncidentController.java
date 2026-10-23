package com.signalops.core.incident.controller;

import com.signalops.core.incident.dto.CreateIncidentRequest;
import com.signalops.core.incident.dto.TriageResultResponse;
import com.signalops.core.incident.dto.UpdateIncidentStatusRequest;
import com.signalops.core.incident.enums.IncidentStatus;
import com.signalops.core.incident.service.TriageResultService;
import com.signalops.core.incident.utils.IncidentRequestHasher;
import com.signalops.core.incident.enums.Severity;
import com.signalops.core.incident.model.Incident;
import com.signalops.core.incident.service.IncidentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {
    private final IncidentService incidentService;
    private final IncidentRequestHasher incidentRequestHasher;
    private final TriageResultService triageResultService;

    public IncidentController(IncidentService incidentService, IncidentRequestHasher hasher, TriageResultService triageResultService) {
        this.incidentService = incidentService;
        this.incidentRequestHasher = hasher;
        this.triageResultService = triageResultService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Incident> createIncident(@Valid @RequestBody CreateIncidentRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IncidentService.IncidentCreationResult result = incidentService.createIncident(
                request.title(),
                request.description(),
                request.severity(),
                request.source(),
                request.metadata(),
                idempotencyKey,
                incidentRequestHasher.hash(request)
        );
        HttpStatus status = result.replayed()
                ? HttpStatus.OK
                : HttpStatus.CREATED;

        return ResponseEntity.status(status)
                .header("Idempotency-Replayed",
                        String.valueOf(result.replayed()))
                .body(result.incident());
    }

    @GetMapping("/{incidentId}/triage")
    public TriageResultResponse getTriageResult(
            @PathVariable UUID incidentId
    ) {
        return TriageResultResponse.from(
                triageResultService.getLatestForIncident(incidentId)
        );
    }

    @GetMapping("/{incidentId}")
    public Incident getIncident(@PathVariable UUID incidentId) {
        return incidentService.getIncident(incidentId);
    }

    @PatchMapping("/{incidentId}/status")
    public Incident updateIncidentStatus(
            @PathVariable UUID incidentId,
            @Valid @RequestBody UpdateIncidentStatusRequest request
    ) {
        return incidentService.updateIncidentStatus(
                incidentId,
                request.status()
        );
    }

    @GetMapping
    public List<Incident> getAllIncidents(
            @RequestParam(required = false) IncidentStatus status
    ) {
        if (status != null) {
            return incidentService.getIncidentsByStatus(status);
        }

        return incidentService.getAllIncidents();
    }
}
