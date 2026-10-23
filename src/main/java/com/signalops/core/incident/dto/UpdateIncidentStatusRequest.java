package com.signalops.core.incident.dto;

import com.signalops.core.incident.enums.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateIncidentStatusRequest(
        @NotNull IncidentStatus status
) {}