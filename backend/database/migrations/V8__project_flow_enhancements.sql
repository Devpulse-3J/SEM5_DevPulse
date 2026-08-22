-- =====================================================================
-- Project management flow: columns the create/link/invite endpoints need.
--
-- Append-only: every statement is ADD COLUMN IF NOT EXISTS or CREATE INDEX
-- IF NOT EXISTS. Nothing here rewrites or drops existing data.
--
-- Deliberately NOT included, because the audit confirmed they already exist:
--   * repos.project_id -> projects(project_id) FK          (V1, line 94)
--   * project_members.role CHECK ('manager','developer')   (V1, line 69-70)
--   * projects.github_repo_url                             (V7)
-- =====================================================================

BEGIN;

-- The admin Create Project form collects a description; there was nowhere to
-- put it.
ALTER TABLE projects
  ADD COLUMN IF NOT EXISTS description text;

-- Per-repo webhook secret. The service currently validates every inbound
-- delivery against one global GITHUB_WEBHOOK_SECRET, so one leaked secret
-- compromises every repo. last_synced_at backs GET /projects/{id}/github/status.
ALTER TABLE repos
  ADD COLUMN IF NOT EXISTS webhook_secret varchar(255),
  ADD COLUMN IF NOT EXISTS last_synced_at timestamptz;

-- POST /projects/{id}/invite may create a placeholder user for an email that
-- has no account. That row gets a random password hash which nobody knows, so
-- it must be flagged as unusable until the user resets it.
--
-- NOT NULL DEFAULT false is safe on an existing table: every current row is an
-- account whose owner chose their own password.
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS must_reset_password boolean NOT NULL DEFAULT false;

-- integration-service looks a repo up by (company_id, project_id) on every
-- link/status/sync call. idx_repos_project covers project_id alone; this pairs
-- it with the tenant so the lookup stays a single index scan.
CREATE INDEX IF NOT EXISTS idx_repos_company_project
  ON repos (company_id, project_id);

COMMIT;
