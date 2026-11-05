package com.signalops.core.evidence.logs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IngestApplicationLogRequest(
        @NotBlank
        @Size(max = 100)
        String serviceName,

        @NotNull
        Instant occurredAt,

        @NotBlank
        @Pattern(regexp = "TRACE|DEBUG|INFO|WARN|ERROR")
        String level,

        @NotBlank
        String message,

        UUID incidentId,

        Map<String, Object> attributes
) {
}
