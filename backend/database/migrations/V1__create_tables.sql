-- =====================================================================
-- DevOps / DORA metrics platform - runnable PostgreSQL schema
-- Generated from database.sql (dbdiagram export), with identity PKs,
-- NOT NULL constraints, defaults, CHECK constraints and named FKs.
--



BEGIN;

-- Drping
DROP TABLE IF EXISTS pr_predictions   CASCADE;
DROP TABLE IF EXISTS dora_metrics     CASCADE;
DROP TABLE IF EXISTS raw_event_log    CASCADE;
DROP TABLE IF EXISTS notifications    CASCADE;
DROP TABLE IF EXISTS alerts           CASCADE;
DROP TABLE IF EXISTS alert_rules      CASCADE;
DROP TABLE IF EXISTS deployments      CASCADE;
DROP TABLE IF EXISTS jira_issues      CASCADE;
DROP TABLE IF EXISTS commits          CASCADE;
DROP TABLE IF EXISTS pr_reviews       CASCADE;
DROP TABLE IF EXISTS pull_requests    CASCADE;
DROP TABLE IF EXISTS repos            CASCADE;
DROP TABLE IF EXISTS integrations     CASCADE;
DROP TABLE IF EXISTS project_members  CASCADE;
DROP TABLE IF EXISTS projects         CASCADE;
DROP TABLE IF EXISTS users            CASCADE;
DROP TABLE IF EXISTS companies        CASCADE;

-- ---------------------------------------------------------------- tenants
CREATE TABLE companies (
  company_id        integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_name      varchar(255) NOT NULL,
  subscription_plan varchar(50)  NOT NULL DEFAULT 'free'
                    CHECK (subscription_plan IN ('free', 'pro', 'enterprise')),
  created_at        timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE users (
  user_id         integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id      integer      NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  email           varchar(320) NOT NULL UNIQUE,
  full_name       varchar(255) NOT NULL,
  password_hash   varchar(255) NOT NULL,
  system_role     varchar(50)  NOT NULL DEFAULT 'member'
                  CHECK (system_role IN ('admin', 'member')),
  github_id       bigint,
  jira_account_id varchar(128),
  created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_company ON users (company_id);

-- --------------------------------------------------------------- projects
CREATE TABLE projects (
  project_id       integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id       integer      NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  project_name     varchar(255) NOT NULL,
  jira_project_key varchar(50),
  created_at       timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_company ON projects (company_id);

CREATE TABLE project_members (
  membership_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  project_id    integer     NOT NULL REFERENCES projects (project_id) ON DELETE CASCADE,
  user_id       integer     NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
  role          varchar(50) NOT NULL DEFAULT 'developer'
                CHECK (role IN ('manager', 'developer')),
  joined_at     timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_project_members_user_project UNIQUE (user_id, project_id)
);

-- ----------------------------------------------------------- integrations
CREATE TABLE integrations (
  integration_id       integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id           integer     NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  provider             varchar(50) NOT NULL CHECK (provider IN ('github', 'jira', 'slack')),
  external_id          varchar(128),
  access_token         text,
  refresh_token        text,
  webhook_secret       varchar(255),
  connected_by_user_id integer     REFERENCES users (user_id) ON DELETE SET NULL,
  connected_at         timestamptz NOT NULL DEFAULT now(),
  is_active            boolean     NOT NULL DEFAULT true,
  CONSTRAINT uq_integrations_company_provider UNIQUE (company_id, provider)
);

-- ------------------------------------------------------------------- code
CREATE TABLE repos (
  repo_id        integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id     integer      NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  project_id     integer      REFERENCES projects (project_id) ON DELETE SET NULL,
  github_repo_id bigint       NOT NULL,
  repo_name      varchar(255) NOT NULL,
  owner_name     varchar(255) NOT NULL,
  full_name      varchar(511) NOT NULL,
  default_branch varchar(255) NOT NULL DEFAULT 'main',
  CONSTRAINT uq_repos_company_github UNIQUE (company_id, github_repo_id)
);

CREATE INDEX idx_repos_project ON repos (project_id);

CREATE TABLE pull_requests (
  pr_id            integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id       integer       NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  repo_id          integer       NOT NULL REFERENCES repos (repo_id) ON DELETE CASCADE,
  github_pr_number integer       NOT NULL,
  title            varchar(1024) NOT NULL,
  author_id        integer       REFERENCES users (user_id) ON DELETE SET NULL,
  base_branch      varchar(255)  NOT NULL DEFAULT 'main',
  is_draft         boolean       NOT NULL DEFAULT false,
  state            varchar(20)   NOT NULL DEFAULT 'open'
                   CHECK (state IN ('open', 'closed', 'merged')),
  lines_added      integer       NOT NULL DEFAULT 0 CHECK (lines_added   >= 0),
  lines_deleted    integer       NOT NULL DEFAULT 0 CHECK (lines_deleted >= 0),
  files_changed    integer       NOT NULL DEFAULT 0 CHECK (files_changed >= 0),
  created_at       timestamptz   NOT NULL DEFAULT now(),
  first_review_at  timestamptz,
  merged_at        timestamptz,
  closed_at        timestamptz,
  CONSTRAINT uq_pull_requests_repo_number UNIQUE (repo_id, github_pr_number)
);

CREATE INDEX idx_pull_requests_author ON pull_requests (author_id);
CREATE INDEX idx_pull_requests_state  ON pull_requests (company_id, state);

CREATE TABLE pr_reviews (
  review_id    integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id   integer     NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  pr_id        integer     NOT NULL REFERENCES pull_requests (pr_id) ON DELETE CASCADE,
  reviewer_id  integer     REFERENCES users (user_id) ON DELETE SET NULL,
  review_state varchar(30) NOT NULL
               CHECK (review_state IN ('approved', 'changes_requested', 'commented', 'dismissed')),
  reviewed_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_pr_reviews_pr ON pr_reviews (pr_id);

CREATE TABLE commits (
  commit_sha    varchar(40) PRIMARY KEY,
  company_id    integer     NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  repo_id       integer     NOT NULL REFERENCES repos (repo_id) ON DELETE CASCADE,
  pr_id         integer     REFERENCES pull_requests (pr_id) ON DELETE SET NULL,
  author_id     integer     REFERENCES users (user_id) ON DELETE SET NULL,
  message       text,
  commit_time   timestamptz NOT NULL,
  lines_added   integer     NOT NULL DEFAULT 0 CHECK (lines_added   >= 0),
  lines_deleted integer     NOT NULL DEFAULT 0 CHECK (lines_deleted >= 0)
);

CREATE INDEX idx_commits_repo_time ON commits (repo_id, commit_time DESC);
CREATE INDEX idx_commits_pr        ON commits (pr_id);

-- ------------------------------------------------------------------- jira
CREATE TABLE jira_issues (
  issue_id     integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id   integer     NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  project_id   integer     REFERENCES projects (project_id) ON DELETE SET NULL,
  jira_key     varchar(50) NOT NULL,
  summary      text,
  issue_type   varchar(50),
  priority     varchar(50),
  status       varchar(50) NOT NULL DEFAULT 'To Do',
  story_points integer     CHECK (story_points IS NULL OR story_points >= 0),
  assignee_id  integer     REFERENCES users (user_id) ON DELETE SET NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  closed_at    timestamptz,
  CONSTRAINT uq_jira_issues_company_key UNIQUE (company_id, jira_key)
);

CREATE INDEX idx_jira_issues_assignee ON jira_issues (assignee_id);

-- ------------------------------------------------------------ deployments
CREATE TABLE deployments (
  deployment_id        integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id           integer     NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  project_id           integer     REFERENCES projects (project_id) ON DELETE SET NULL,
  commit_sha           varchar(40) REFERENCES commits (commit_sha) ON DELETE SET NULL,
  environment          varchar(50) NOT NULL DEFAULT 'production'
                       CHECK (environment IN ('development', 'staging', 'production')),
  status               varchar(20) NOT NULL DEFAULT 'pending'
                       CHECK (status IN ('pending', 'success', 'failed', 'rolled_back')),
  deployed_at          timestamptz NOT NULL DEFAULT now(),
  failure_recovered_at timestamptz,
  triggered_by_user_id integer     REFERENCES users (user_id) ON DELETE SET NULL
);

CREATE INDEX idx_deployments_project_time ON deployments (project_id, deployed_at DESC);

-- ----------------------------------------------------------------- alerts
CREATE TABLE alert_rules (
  rule_id            integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id         integer     NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  project_id         integer     REFERENCES projects (project_id) ON DELETE CASCADE,
  rule_type          varchar(50) NOT NULL,
  threshold_hours    integer     CHECK (threshold_hours IS NULL OR threshold_hours > 0),
  slack_channel      varchar(255),
  is_active          boolean     NOT NULL DEFAULT true,
  created_by_user_id integer     REFERENCES users (user_id) ON DELETE SET NULL,
  created_at         timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE alerts (
  alert_id     integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id   integer     NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  project_id   integer     REFERENCES projects (project_id) ON DELETE CASCADE,
  rule_id      integer     REFERENCES alert_rules (rule_id) ON DELETE SET NULL,
  entity_type  varchar(50) NOT NULL
               CHECK (entity_type IN ('pull_request', 'deployment', 'jira_issue', 'repo')),
  entity_id    integer,
  severity     varchar(20) NOT NULL DEFAULT 'warning'
               CHECK (severity IN ('info', 'warning', 'critical')),
  message      text,
  triggered_at timestamptz NOT NULL DEFAULT now(),
  resolved_at  timestamptz
);

CREATE INDEX idx_alerts_open ON alerts (company_id, triggered_at DESC) WHERE resolved_at IS NULL;

CREATE TABLE notifications (
  notification_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id      integer     NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  alert_id        integer     NOT NULL REFERENCES alerts (alert_id) ON DELETE CASCADE,
  user_id         integer     REFERENCES users (user_id) ON DELETE CASCADE,
  channel         varchar(20) NOT NULL CHECK (channel IN ('email', 'slack', 'in_app')),
  status          varchar(20) NOT NULL DEFAULT 'pending'
                  CHECK (status IN ('pending', 'sent', 'failed', 'read')),
  sent_at         timestamptz
);

CREATE INDEX idx_notifications_user ON notifications (user_id, status);

-- ------------------------------------------------------ ingestion + DORA
CREATE TABLE raw_event_log (
  event_id         integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id       integer      NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  provider         varchar(50)  NOT NULL CHECK (provider IN ('github', 'jira', 'slack')),
  event_type       varchar(100) NOT NULL,
  payload          jsonb        NOT NULL,
  received_at      timestamptz  NOT NULL DEFAULT now(),
  processed_at     timestamptz,
  processing_error text
);

-- Worker queue: unprocessed events first.
CREATE INDEX idx_raw_event_log_unprocessed
  ON raw_event_log (received_at) WHERE processed_at IS NULL;

CREATE TABLE dora_metrics (
  metric_id            integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id           integer NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  project_id           integer NOT NULL REFERENCES projects (project_id) ON DELETE CASCADE,
  calculated_date      date    NOT NULL,
  window_days          integer NOT NULL DEFAULT 30 CHECK (window_days > 0),
  deployment_frequency numeric(10, 4),
  lead_time_hours      numeric(10, 2),
  change_failure_rate  numeric(5, 4) CHECK (change_failure_rate BETWEEN 0 AND 1),
  mttr_hours           numeric(10, 2),
  CONSTRAINT uq_dora_metrics_project_date_window
    UNIQUE (project_id, calculated_date, window_days)
);

-- ------------------------------------------------------------ ML scoring
CREATE TABLE pr_predictions (
  prediction_id     integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id        integer     NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
  pr_id             integer     NOT NULL REFERENCES pull_requests (pr_id) ON DELETE CASCADE,
  algorithm         varchar(50) NOT NULL
                    CHECK (algorithm IN ('random_forest', 'xgboost', 'logistic_regression')),
  model_version     varchar(20) NOT NULL,
  prediction_type   varchar(50) NOT NULL
                    CHECK (prediction_type IN ('stale_risk', 'high_risk_code')),
  risk_category     varchar(20) NOT NULL
                    CHECK (risk_category IN ('low', 'medium', 'high')),
  risk_score        numeric(5, 4) NOT NULL CHECK (risk_score BETWEEN 0 AND 1),
  confidence        numeric(5, 4) CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  predicted_at      timestamptz NOT NULL DEFAULT now(),
  -- Ground truth, backfilled once the PR reaches a terminal state.
  actual_outcome    varchar(20)
                    CHECK (actual_outcome IS NULL OR actual_outcome IN
                          ('merged', 'became_stale', 'closed', 'still_open')),
  actual_outcome_at timestamptz
);

CREATE INDEX idx_pr_predictions_company_risk
  ON pr_predictions (company_id, risk_category, predicted_at DESC);
CREATE INDEX idx_pr_predictions_pr
  ON pr_predictions (pr_id, predicted_at DESC);

COMMIT;