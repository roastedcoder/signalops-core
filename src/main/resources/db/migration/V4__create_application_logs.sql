-- Store structured application log evidence for incident investigations.

CREATE TABLE application_logs (
                                  id UUID PRIMARY KEY,
                                  service_name VARCHAR(100) NOT NULL,
                                  occurred_at TIMESTAMPTZ NOT NULL,
                                  level VARCHAR(10) NOT NULL,
                                  message TEXT NOT NULL,
                                  incident_id UUID REFERENCES incidents(id),
                                  attributes JSONB NOT NULL DEFAULT '{}'::jsonb,

                                  CONSTRAINT chk_application_logs_level
                                      CHECK (level IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'))
);

CREATE INDEX idx_application_logs_service_time
    ON application_logs (service_name, occurred_at DESC);

CREATE INDEX idx_application_logs_incident_time
    ON application_logs (incident_id, occurred_at DESC)
    WHERE incident_id IS NOT NULL;
