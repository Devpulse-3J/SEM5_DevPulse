-- =====================================================================
-- Lets notification-service raise an alert at most once per real-world event.
--
-- One failed deployment reaches the service several times: GitHub reports it
-- as both a workflow_job and a deployment_status event (with different ids),
-- and deliveries can be repeated. A unique dedup_key makes "have we already
-- alerted for this?" a database guarantee rather than a check-then-insert race.
--
-- Nullable and partial: existing alerts and alert types that don't need it
-- are unaffected.
-- =====================================================================

BEGIN;

ALTER TABLE alerts
  ADD COLUMN IF NOT EXISTS dedup_key varchar(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_alerts_dedup_key
  ON alerts (dedup_key)
  WHERE dedup_key IS NOT NULL;

COMMIT;
