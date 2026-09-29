-- =====================================================================
-- Persisted Jira OAuth connection state, owned by integration-service.
--
-- Not the existing `integrations` table: that one is owned by auth-service
-- (see CLAUDE.md's table-ownership list), and integration-service may only
-- read it, not write it. This is a new, integration-service-owned table for
-- exactly the same kind of data (access/refresh tokens, connected site),
-- scoped to the jira provider, so the ownership rule stays intact.
-- =====================================================================

BEGIN;

CREATE TABLE jira_connections (
  connection_id        integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id           integer      NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  cloud_id             varchar(128) NOT NULL,
  site_url             varchar(500),
  site_name            varchar(255),
  access_token         text         NOT NULL,
  refresh_token        text,
  connected_by_user_id integer      REFERENCES users (user_id) ON DELETE SET NULL,
  connected_at         timestamptz  NOT NULL DEFAULT now(),
  updated_at           timestamptz  NOT NULL DEFAULT now(),
  is_active            boolean      NOT NULL DEFAULT true,
  CONSTRAINT uq_jira_connections_company UNIQUE (company_id)
);

CREATE INDEX idx_jira_connections_company_active
  ON jira_connections (company_id)
  WHERE is_active;

COMMIT;
