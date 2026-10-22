
ALTER TABLE triage_results
    ADD COLUMN rationale VARCHAR(1000);

UPDATE triage_results
SET rationale = 'Not recorded for this triage result'
WHERE rationale IS NULL;

ALTER TABLE triage_results
    ALTER COLUMN rationale SET NOT NULL;
