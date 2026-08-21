-- =====================================================================
-- Metrics-service API and ingestion support.
--
-- V1 contains enough data for the four DORA calculations.  This migration
-- adds only the fields required by the documented pull-request API and by
-- idempotent processing of GitHub PR/deployment events.
-- =====================================================================

BEGIN;

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS avatar_url varchar(512);

ALTER TABLE pull_requests
  ADD COLUMN IF NOT EXISTS github_pr_id bigint,
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS head_branch varchar(255),
  ADD COLUMN IF NOT EXISTS url varchar(512),
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS uq_pull_requests_repo_github_id
  ON pull_requests (repo_id, github_pr_id)
  WHERE github_pr_id IS NOT NULL;

ALTER TABLE pr_reviews DROP CONSTRAINT IF EXISTS pr_reviews_review_state_check;
ALTER TABLE pr_reviews
  ADD CONSTRAINT pr_reviews_review_state_check
  CHECK (review_state IN ('pending', 'approved', 'changes_requested', 'commented', 'dismissed'));

CREATE TABLE IF NOT EXISTS pr_checks (
  check_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  pr_id    integer      NOT NULL REFERENCES pull_requests (pr_id) ON DELETE CASCADE,
  name     varchar(255) NOT NULL,
  status   varchar(20)  NOT NULL
           CHECK (status IN ('SUCCESS', 'FAILURE', 'IN_PROGRESS', 'QUEUED')),
  url      varchar(512),
  CONSTRAINT uq_pr_checks_pr_name UNIQUE (pr_id, name)
);

CREATE INDEX IF NOT EXISTS idx_pr_checks_pr ON pr_checks (pr_id);

ALTER TABLE deployments
  ADD COLUMN IF NOT EXISTS github_deployment_id bigint;

CREATE UNIQUE INDEX IF NOT EXISTS uq_deployments_company_github_id
  ON deployments (company_id, github_deployment_id)
  WHERE github_deployment_id IS NOT NULL;

-- Supports all four DORA calculations with tenant/project isolation and the
-- production/time-window predicates used by metrics-service.
CREATE INDEX IF NOT EXISTS idx_deployments_dora_window
  ON deployments (company_id, project_id, environment, deployed_at DESC)
  INCLUDE (status, commit_sha, failure_recovered_at);

ALTER TABLE dora_metrics
  ADD COLUMN IF NOT EXISTS calculated_at timestamptz NOT NULL DEFAULT now();

COMMIT;
