-- =====================================================================
-- GitHub App installations, owned by integration-service.
--
-- When an admin installs the GitHub App, GitHub redirects back with an
-- installation_id. Recording which company it belongs to is what lets the
-- admin page list exactly the repositories that installation was granted,
-- using an installation token instead of a personal access token.
--
-- installation_id is the primary key so an installation can belong to one
-- company only: the first company to claim it keeps it.
-- =====================================================================

BEGIN;

CREATE TABLE github_installations (
  installation_id bigint       PRIMARY KEY,
  company_id      integer      NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  account_login   varchar(255),
  account_type    varchar(50),
  created_at      timestamptz  NOT NULL DEFAULT now(),
  updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_github_installations_company ON github_installations (company_id);

COMMIT;
