-- =====================================================================
-- Migration V9: invitations addressed to an EMAIL, not to a users row.
--
-- Problem this fixes
-- ------------------
-- project_invitations.user_id is NOT NULL, so an invite can only ever point
-- at a users row that already exists. That forced POST /projects/{id}/invite
-- to pre-create a placeholder users row for an unknown address (the
-- must_reset_password flag added in V8), which means an invite WRITES to the
-- users table. When the address already has an account that insert collides
-- with the UNIQUE constraint on users.email, and the invite fails.
--
-- After this migration an invitation can name the invitee by email alone, so
-- the invite flow never has to touch users. The users row is created exactly
-- once, by registration, and the pending invitation is turned into a
-- project_members row at that moment.
--
-- Append-only: every statement is IF EXISTS / IF NOT EXISTS guarded, no
-- existing row is rewritten or dropped. project_invitations is empty of
-- email-less semantics today, so relaxing user_id breaks nothing.
--
-- NOTE: this migration removes the NEED for the placeholder-user insert. It
-- does not by itself stop the code from doing it — ProjectMemberServiceImpl
-- .inviteByEmail() must be changed to write a project_invitations row instead
-- of a users row, and AuthServiceImpl.register() to consume pending invites
-- for the address being registered. See the bottom of this file.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. An invitation may now identify its invitee by email instead of by id.
--    user_id stays for invites sent to someone who already has an account:
--    that case never needed a placeholder and still resolves immediately.
-- ---------------------------------------------------------------------
ALTER TABLE project_invitations
  ADD COLUMN IF NOT EXISTS email      varchar(320),
  ADD COLUMN IF NOT EXISTS token      varchar(255),
  ADD COLUMN IF NOT EXISTS expires_at timestamptz;

ALTER TABLE project_invitations
  ALTER COLUMN user_id DROP NOT NULL;

-- An invitation with neither an id nor an address points at nobody.
ALTER TABLE project_invitations
  DROP CONSTRAINT IF EXISTS ck_project_invitations_target;
ALTER TABLE project_invitations
  ADD CONSTRAINT ck_project_invitations_target
  CHECK (user_id IS NOT NULL OR email IS NOT NULL);

-- ---------------------------------------------------------------------
-- 2. An email invite can now go stale, so 'expired' joins the status set.
--    V5 created this check inline, hence the generated name being dropped.
-- ---------------------------------------------------------------------
ALTER TABLE project_invitations
  DROP CONSTRAINT IF EXISTS project_invitations_status_check;
ALTER TABLE project_invitations
  DROP CONSTRAINT IF EXISTS ck_project_invitations_status;
ALTER TABLE project_invitations
  ADD CONSTRAINT ck_project_invitations_status
  CHECK (status IN ('pending', 'accepted', 'rejected', 'expired'));

-- ---------------------------------------------------------------------
-- 3. One live invite per address per project.
--
--    uq_project_invitations_user_project still guards the by-id case, and
--    does NOT guard the by-email case: Postgres treats NULLs as distinct, so
--    every email-only row has a NULL user_id and slips past it. Hence a
--    partial index over the address instead.
--
--    Lowercased because the service lowercases before it looks up, and
--    users.email is compared case-sensitively today — matching on lower()
--    here keeps 'A@x.com' and 'a@x.com' from becoming two open invites.
--
--    Partial on 'pending' on purpose: once an invite is accepted, rejected or
--    expired, the same person may legitimately be invited again.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_project_invites_pending_email
  ON project_invitations (project_id, lower(email))
  WHERE email IS NOT NULL AND status = 'pending';

-- Registration looks up every pending invite for the address being claimed.
CREATE INDEX IF NOT EXISTS idx_project_invites_email
  ON project_invitations (lower(email))
  WHERE email IS NOT NULL;

-- Accept-by-link. Partial so the rows that carry no token stay unconstrained.
CREATE UNIQUE INDEX IF NOT EXISTS uq_project_invites_token
  ON project_invitations (token)
  WHERE token IS NOT NULL;

-- ---------------------------------------------------------------------
-- 4. The invite has to remember which company sent it.
--    Without this, claiming an invite cannot tell which company the new user
--    belongs to without joining back through projects, and an invite whose
--    project is deleted loses that information entirely.
-- ---------------------------------------------------------------------
ALTER TABLE project_invitations
  ADD COLUMN IF NOT EXISTS company_id integer;

ALTER TABLE project_invitations
  DROP CONSTRAINT IF EXISTS fk_project_invitations_company;
ALTER TABLE project_invitations
  ADD CONSTRAINT fk_project_invitations_company
  FOREIGN KEY (company_id) REFERENCES companies (company_id) ON DELETE CASCADE;

UPDATE project_invitations pi
   SET company_id = p.company_id
  FROM projects p
 WHERE p.project_id = pi.project_id
   AND pi.company_id IS NULL;

COMMIT;

-- =====================================================================
-- SUPERSEDED — retained because it has already been applied
-- =====================================================================
-- The email-invitation design above was dropped in favour of a simpler rule:
-- an admin may only invite someone who already has an account, so
-- POST /projects/{id}/invite returns 404 for an unregistered address and never
-- records an email invitation. The columns this migration adds (email, token,
-- expires_at, company_id) are therefore unused by application code.
--
-- They are left in place rather than reverted: this file has run against the
-- live database, and every column it adds is nullable, so nothing depends on
-- them and nothing breaks by their being there. Do NOT edit or delete this
-- file — if the columns are ever worth removing, that is a new V10.
--
-- What DID survive from this work, and is what actually fixes the bug:
--   * inviteByEmail() no longer writes to users under any circumstance
--   * the existing-account lookup is case-insensitive, so an account stored as
--     'A@x.com' is not treated as unregistered when 'a@x.com' is invited
-- =====================================================================
