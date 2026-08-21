-- =====================================================================
-- Migration V5: Organization Invitations, Project Invitations,
-- and Workspace Join Requests (GitHub User Join Workflow)
-- =====================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS organization_invitations (
  invitation_id       integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id          integer      NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  email               varchar(320) NOT NULL,
  role                varchar(50)  NOT NULL DEFAULT 'member'
                      CHECK (role IN ('admin', 'member')),
  token               varchar(255) NOT NULL UNIQUE,
  invited_by_user_id  integer      REFERENCES users (user_id) ON DELETE SET NULL,
  status              varchar(50)  NOT NULL DEFAULT 'pending'
                      CHECK (status IN ('pending', 'accepted', 'rejected', 'expired')),
  expires_at          timestamptz  NOT NULL,
  created_at          timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_org_invites_company ON organization_invitations (company_id);
CREATE INDEX IF NOT EXISTS idx_org_invites_token ON organization_invitations (token);

CREATE TABLE IF NOT EXISTS workspace_join_requests (
  request_id          integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id          integer      NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  user_id             integer      NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
  github_username     varchar(255),
  message             text,
  status              varchar(50)  NOT NULL DEFAULT 'pending'
                      CHECK (status IN ('pending', 'approved', 'rejected')),
  requested_at        timestamptz  NOT NULL DEFAULT now(),
  reviewed_by_user_id integer      REFERENCES users (user_id) ON DELETE SET NULL,
  reviewed_at         timestamptz,
  CONSTRAINT uq_workspace_join_requests_user_company UNIQUE (user_id, company_id)
);

CREATE INDEX IF NOT EXISTS idx_join_requests_company ON workspace_join_requests (company_id);
CREATE INDEX IF NOT EXISTS idx_join_requests_user ON workspace_join_requests (user_id);

CREATE TABLE IF NOT EXISTS project_invitations (
  invitation_id       integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  project_id          integer      NOT NULL REFERENCES projects (project_id) ON DELETE CASCADE,
  user_id             integer      NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
  role                varchar(50)  NOT NULL DEFAULT 'developer'
                      CHECK (role IN ('manager', 'developer')),
  invited_by_user_id  integer      REFERENCES users (user_id) ON DELETE SET NULL,
  status              varchar(50)  NOT NULL DEFAULT 'pending'
                      CHECK (status IN ('pending', 'accepted', 'rejected')),
  created_at          timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_project_invitations_user_project UNIQUE (user_id, project_id)
);

CREATE INDEX IF NOT EXISTS idx_project_invites_project ON project_invitations (project_id);

COMMIT;
