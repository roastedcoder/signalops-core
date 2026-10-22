
-- V1: Create the initial database schema for SignalOps.
-- Includes incidents, idempotency records, transactional outbox events,
-- and incident audit history.
-- Future schema changes should be added as new versioned Flyway migrations.


-- Stores incidents and their lifecycle state.
CREATE TABLE incidents (
                           id UUID PRIMARY KEY,
                           title VARCHAR(200) NOT NULL,
                           description TEXT,
                           severity VARCHAR(20) NOT NULL,
                           status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                           source VARCHAR(100) NOT NULL,
                           metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
                           created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                           updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                           version BIGINT NOT NULL DEFAULT 0,

                           CONSTRAINT chk_incidents_severity
                               CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),

                           CONSTRAINT chk_incidents_status
                               CHECK (
                                   status IN (
                                              'OPEN',
                                              'ACKNOWLEDGED',
                                              'INVESTIGATING',
                                              'RESOLVED'
                                       )
                                   )
);

CREATE INDEX idx_incidents_created_at
    ON incidents (created_at DESC, id DESC);

CREATE INDEX idx_incidents_status_severity
    ON incidents (status, severity);


-- Prevent duplicate incident creation for the same request key.
CREATE TABLE idempotency_records (
                                     idempotency_key VARCHAR(128) PRIMARY KEY,
                                     request_hash VARCHAR(64) NOT NULL,
                                     incident_id UUID NOT NULL REFERENCES incidents(id),
                                     created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                     expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_records_expires_at
    ON idempotency_records (expires_at);


-- Store events durably for asynchronous processing and retries.
CREATE TABLE outbox_events (
                               id UUID PRIMARY KEY,
                               aggregate_id UUID NOT NULL REFERENCES incidents(id),
                               event_type VARCHAR(100) NOT NULL,
                               payload JSONB NOT NULL,
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               attempt_count INT NOT NULL DEFAULT 0,
                               next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               locked_until TIMESTAMPTZ,
                               locked_by VARCHAR(128),
                               created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               published_at TIMESTAMPTZ,
                               last_error TEXT,

                               CONSTRAINT chk_outbox_status
                                   CHECK (
                                       status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')
                                       ),

                               CONSTRAINT chk_outbox_attempt_count
                                   CHECK (attempt_count >= 0)
);

CREATE INDEX idx_outbox_pending_work
    ON outbox_events (next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_expired_leases
    ON outbox_events (locked_until)
    WHERE status = 'PROCESSING';


-- Preserve a history of incident lifecycle changes.
CREATE TABLE incident_audit (
                                id UUID PRIMARY KEY,
                                incident_id UUID NOT NULL REFERENCES incidents(id),
                                action VARCHAR(100) NOT NULL,
                                previous_status VARCHAR(20),
                                new_status VARCHAR(20),
                                occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                details JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_incident_audit_incident_time
    ON incident_audit (incident_id, occurred_at DESC);
