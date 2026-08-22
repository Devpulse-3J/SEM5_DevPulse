-- =====================================================================
-- Link a project to the GitHub repository it tracks.
--
-- Until now `projects` could reference Jira (jira_project_key) but had no
-- way to record a GitHub repo, so integration-service could only be told
-- which repo to sync by hand (owner/repo query params on
-- POST /integrations/github/sync).
--
-- auth-service owns this column and stores the URL exactly as the admin
-- entered it. It deliberately does NOT store owner/name: splitting a
-- GitHub URL is GitHub knowledge, and that belongs to integration-service,
-- which already derives owner/name and persists them on `repos`.
-- =====================================================================

BEGIN;

ALTER TABLE projects
  ADD COLUMN IF NOT EXISTS github_repo_url varchar(512);

COMMIT;
