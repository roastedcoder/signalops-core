package com.signalops.core.incident.dto;

import com.signalops.core.incident.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateIncidentRequest(
        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 500)
        String description,

        @NotNull
        Severity severity,

        @NotBlank
        @Size(max = 100)
        String source,

        @Size(max = 1000)
        Map<String, Object> metadata
) {}