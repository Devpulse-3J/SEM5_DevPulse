-- =====================================================================
-- V3__seed_demo_data.sql
--
-- Minimal demo data for LOCAL development only: one company, an admin and
-- an ordinary member, one project, and their per-project memberships
-- (manager + developer) so the per-project RBAC model can be exercised
-- end to end.
--
-- Do NOT rely on this in production. The password_hash values below are
-- placeholders, NOT real hashes — register through auth-service (or replace
-- them with real bcrypt hashes) before you can actually log in.
-- =====================================================================

-- Company (tenant)
INSERT INTO companies (company_name, subscription_plan)
VALUES ('DevPulse Demo', 'pro');

-- Users: one company admin, one ordinary member
INSERT INTO users (company_id, email, full_name, password_hash, system_role)
VALUES
  ((SELECT company_id FROM companies WHERE company_name = 'DevPulse Demo'),
   'admin@demo.devpulse', 'Demo Admin', 'REPLACE_ME_bcrypt_hash', 'admin'),
  ((SELECT company_id FROM companies WHERE company_name = 'DevPulse Demo'),
   'dev@demo.devpulse',   'Demo Dev',   'REPLACE_ME_bcrypt_hash', 'member');

-- Project
INSERT INTO projects (company_id, project_name, jira_project_key)
VALUES
  ((SELECT company_id FROM companies WHERE company_name = 'DevPulse Demo'),
   'payments-api', 'PAY');

-- Per-project memberships: the admin manages this project, the member develops on it.
-- (Company-level role and per-project role are independent — this is the whole point
--  of resolving permission as (user, project) -> role.)
INSERT INTO project_members (project_id, user_id, role)
VALUES
  ((SELECT project_id FROM projects WHERE project_name = 'payments-api'),
   (SELECT user_id FROM users WHERE email = 'admin@demo.devpulse'),
   'manager'),
  ((SELECT project_id FROM projects WHERE project_name = 'payments-api'),
   (SELECT user_id FROM users WHERE email = 'dev@demo.devpulse'),
   'developer');
