-- =====================================================================
-- Add github_username column to users and unique index on github_id.
-- =====================================================================

BEGIN;

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS github_username varchar(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_github_id
  ON users (github_id)
  WHERE github_id IS NOT NULL;

COMMIT;
