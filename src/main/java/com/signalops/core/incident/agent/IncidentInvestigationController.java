package com.signalops.core.incident.agent;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentInvestigationController {
    private final IncidentInvestigationAgent investigationAgent;

    public IncidentInvestigationController(IncidentInvestigationAgent investigationAgent) {
        this.investigationAgent = investigationAgent;
    }

    @PostMapping("/{incidentId}/investigation")
    public IncidentInvestigationAgent.InvestigationReport investigate(@PathVariable UUID incidentId) {
        return investigationAgent.investigate(incidentId);
    }
}
