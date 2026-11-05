package com.signalops.core.evidence.logs.controller;

import com.signalops.core.evidence.logs.dto.IngestApplicationLogRequest;
import com.signalops.core.evidence.logs.model.ApplicationLog;
import com.signalops.core.evidence.logs.service.ApplicationLogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evidence/logs")
public class ApplicationLogController {

    private final ApplicationLogService applicationLogService;

    public ApplicationLogController(ApplicationLogService applicationLogService) {
        this.applicationLogService = applicationLogService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IngestedApplicationLogResponse ingest(
            @Valid @RequestBody IngestApplicationLogRequest request
    ) {
        ApplicationLog saved = applicationLogService.ingest(
                request.serviceName(),
                request.occurredAt(),
                request.level(),
                request.message(),
                request.incidentId(),
                request.attributes()
        );

        return new IngestedApplicationLogResponse(
                saved.getId(),
                saved.getServiceName(),
                saved.getOccurredAt(),
                saved.getLevel()
        );
    }

    public record IngestedApplicationLogResponse(
            java.util.UUID id,
            String serviceName,
            java.time.Instant occurredAt,
            String level
    ) {
    }
}
