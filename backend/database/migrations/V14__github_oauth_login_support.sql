-- =====================================================================
-- Migration V14: Support GitHub OAuth Login & Sign-Up
--
-- 1. Make users.password_hash nullable (GitHub OAuth users have no local password)
-- 2. Ensure users.github_id exists and has a unique index
-- 3. Ensure users.avatar_url exists
-- 4. Add users.auth_provider with default 'LOCAL'
-- =====================================================================

BEGIN;

-- 1. password / password_hash: Make column nullable
ALTER TABLE users
  ALTER COLUMN password_hash DROP NOT NULL;

-- 2. github_id: Ensure column and unique index exist
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS github_id bigint;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_github_id
  ON users (github_id)
  WHERE github_id IS NOT NULL;

-- 3. avatar_url: Ensure column exists
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS avatar_url varchar(512);

-- 4. auth_provider: Add column with default 'LOCAL'
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS auth_provider varchar(32) NOT NULL DEFAULT 'LOCAL';

COMMIT;
