-- This table will store the triage result generated for each incident.


CREATE TABLE triage_results (
    event_id UUID PRIMARY KEY
        REFERENCES outbox_events(id),

    incident_id UUID NOT NULL
        REFERENCES incidents(id),

    triage_summary TEXT NOT NULL,
    recommended_severity VARCHAR(20) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_triage_results_incident_id
    ON triage_results(incident_id);
