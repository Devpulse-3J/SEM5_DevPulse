-- Realistic dashboard demo data for DevPulse.
-- Target tenant: company_id = 12, project_id = 5.
-- PostgreSQL / Supabase compatible. Safe to run repeatedly.

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM projects WHERE project_id = 5 AND company_id = 12
  ) THEN
    RAISE EXCEPTION 'Project 5 does not exist or does not belong to company 12';
  END IF;
END $$;

-- Six realistic team members. The hash is intentionally unusable: these are
-- dashboard personas, not login accounts.
INSERT INTO users (
  company_id, email, full_name, password_hash, system_role,
  github_id, jira_account_id, avatar_url, must_reset_password, created_at
)
VALUES
  (12, 'maya.perera.mock@devpulse.local',   'Maya Perera',   'MOCK_ACCOUNT_NO_LOGIN', 'manager',   12005001, 'mock-maya-p',   'https://i.pravatar.cc/160?img=47', true, now() - interval '14 months'),
  (12, 'dilan.fernando.mock@devpulse.local','Dilan Fernando','MOCK_ACCOUNT_NO_LOGIN', 'developer', 12005002, 'mock-dilan-f',  'https://i.pravatar.cc/160?img=12', true, now() - interval '11 months'),
  (12, 'sachini.silva.mock@devpulse.local', 'Sachini Silva', 'MOCK_ACCOUNT_NO_LOGIN', 'developer', 12005003, 'mock-sachini-s','https://i.pravatar.cc/160?img=32', true, now() - interval '9 months'),
  (12, 'nuwan.jayasinghe.mock@devpulse.local','Nuwan Jayasinghe','MOCK_ACCOUNT_NO_LOGIN','developer',12005004, 'mock-nuwan-j',  'https://i.pravatar.cc/160?img=11', true, now() - interval '8 months'),
  (12, 'amaya.wickramasinghe.mock@devpulse.local','Amaya Wickramasinghe','MOCK_ACCOUNT_NO_LOGIN','developer',12005005,'mock-amaya-w','https://i.pravatar.cc/160?img=44', true, now() - interval '6 months'),
  (12, 'kasun.ranasinghe.mock@devpulse.local','Kasun Ranasinghe','MOCK_ACCOUNT_NO_LOGIN','developer',12005006,'mock-kasun-r',  'https://i.pravatar.cc/160?img=15', true, now() - interval '4 months')
ON CONFLICT (email) DO UPDATE SET
  company_id = EXCLUDED.company_id,
  full_name = EXCLUDED.full_name,
  system_role = EXCLUDED.system_role,
  github_id = EXCLUDED.github_id,
  jira_account_id = EXCLUDED.jira_account_id,
  avatar_url = EXCLUDED.avatar_url;

INSERT INTO project_members (project_id, user_id, role, joined_at)
SELECT 5, u.user_id,
       CASE WHEN u.email = 'maya.perera.mock@devpulse.local' THEN 'manager' ELSE 'developer' END,
       u.created_at + interval '2 days'
FROM users u
WHERE u.company_id = 12 AND u.email LIKE '%.mock@devpulse.local'
ON CONFLICT (user_id, project_id) DO UPDATE SET role = EXCLUDED.role;

-- One repository dedicated to this seed set. Re-running updates its metadata.
INSERT INTO repos (
  company_id, project_id, github_repo_id, repo_name, owner_name, full_name,
  default_branch, last_synced_at
)
VALUES (
  12, 5, 1200500001, 'devpulse-platform', 'acme-engineering',
  'acme-engineering/devpulse-platform', 'main', now() - interval '8 minutes'
)
ON CONFLICT (company_id, github_repo_id) DO UPDATE SET
  project_id = EXCLUDED.project_id,
  repo_name = EXCLUDED.repo_name,
  owner_name = EXCLUDED.owner_name,
  full_name = EXCLUDED.full_name,
  default_branch = EXCLUDED.default_branch,
  last_synced_at = EXCLUDED.last_synced_at;

-- 24 PRs spread across the last month: 6 open, 16 merged, 2 closed.
WITH seed AS (
  SELECT * FROM (VALUES
    (501, 'Add deployment health overview',          'feature/deployment-health',  'open',   false,  2,  210,  44,  8, NULL::int),
    (502, 'Fix Slack channel refresh after OAuth',   'fix/slack-channel-refresh', 'open',   false,  5,   68,  21,  4, NULL::int),
    (503, 'Refactor repository sync pagination',     'refactor/repo-pagination',  'open',   false,  9,  420, 173, 14, NULL::int),
    (504, 'Add workload capacity warning',           'feature/capacity-warning',  'open',   true,   1,  156,  17,  6, NULL::int),
    (505, 'Cache project membership lookups',        'perf/membership-cache',     'open',   false, 14,   94,  31,  7, NULL::int),
    (506, 'Upgrade dashboard accessibility labels',  'chore/a11y-labels',         'open',   false,  4,  132,  48, 11, NULL::int),
    (507, 'Support failed deployment recovery time', 'feature/mttr-events',       'merged', false,  3,  286,  65, 10, 48),
    (508, 'Validate webhook replay signatures',      'security/webhook-replay',   'merged', false,  5,  174,  52,  8, 31),
    (509, 'Improve DORA empty-state messaging',      'fix/dora-empty-state',      'merged', false,  7,   61,  19,  5, 20),
    (510, 'Add project repository selector',         'feature/repo-selector',     'merged', false,  8,  334,  87, 13, 54),
    (511, 'Normalize Jira priority values',          'fix/jira-priority',         'merged', false, 10,   79,  24,  6, 25),
    (512, 'Emit pull request review events',         'feature/review-events',     'merged', false, 11,  245,  71,  9, 38),
    (513, 'Reduce gateway authentication latency',   'perf/gateway-auth',         'merged', false, 13,  118,  92,  7, 29),
    (514, 'Add notification delivery retry',         'feature/notification-retry','merged',false, 15,  301, 104, 12, 63),
    (515, 'Fix timezone conversion in charts',       'fix/chart-timezones',       'merged', false, 17,   48,  16,  4, 18),
    (516, 'Record GitHub deployment identifiers',    'feature/deployment-ids',    'merged', false, 19,  183,  37,  8, 34),
    (517, 'Tune RabbitMQ consumer concurrency',      'perf/rabbit-consumers',     'merged', false, 21,   92,  45,  5, 27),
    (518, 'Add pull request CI checks',              'feature/pr-checks',         'merged', false, 23,  391, 122, 16, 72),
    (519, 'Handle deleted GitHub users',             'fix/deleted-github-user',   'merged', false, 25,  126,  36,  7, 22),
    (520, 'Improve deployment filtering',            'feature/deploy-filters',    'merged', false, 27,  205,  58,  9, 41),
    (521, 'Add project audit metadata',              'feature/project-audit',     'merged', false, 29,  162,  43,  8, 36),
    (522, 'Fix stale prediction confidence display', 'fix/prediction-confidence','merged', false, 31,   55,  14,  3, 16),
    (523, 'Prototype GraphQL metrics endpoint',      'spike/graphql-metrics',     'closed', true,  18,  611, 204, 21, NULL::int),
    (524, 'Replace Redis rate limiter',              'spike/new-rate-limiter',    'closed', false, 26,  370, 198, 18, NULL::int)
  ) AS v(number, title, head_branch, state, is_draft, age_days,
         additions, deletions, changed_files, merge_hours)
), people AS (
  SELECT user_id, row_number() OVER (ORDER BY github_id) AS rn
  FROM users WHERE company_id = 12 AND email LIKE '%.mock@devpulse.local'
), repo AS (
  SELECT repo_id FROM repos WHERE company_id = 12 AND github_repo_id = 1200500001
)
INSERT INTO pull_requests (
  company_id, repo_id, github_pr_id, github_pr_number, title, description,
  author_id, base_branch, head_branch, is_draft, state, lines_added,
  lines_deleted, files_changed, url, created_at, first_review_at,
  merged_at, closed_at, updated_at
)
SELECT 12, repo.repo_id, 120050000000 + s.number, s.number, s.title,
       'Implements ' || lower(s.title) || ' with tests, observability, and rollout notes.',
       p.user_id, 'main', s.head_branch, s.is_draft, s.state,
       s.additions, s.deletions, s.changed_files,
       'https://github.com/acme-engineering/devpulse-platform/pull/' || s.number,
       now() - make_interval(days => s.age_days),
       CASE WHEN s.state = 'merged' THEN now() - make_interval(days => s.age_days) + interval '6 hours' END,
       CASE WHEN s.state = 'merged' THEN now() - make_interval(days => s.age_days) + make_interval(hours => s.merge_hours) END,
       CASE WHEN s.state = 'closed' THEN now() - make_interval(days => s.age_days - 2) END,
       CASE WHEN s.state = 'open' THEN now() - interval '3 hours'
            ELSE now() - make_interval(days => greatest(s.age_days - 2, 0)) END
FROM seed s
CROSS JOIN repo
JOIN people p ON p.rn = ((s.number - 501) % 6) + 1
ON CONFLICT (repo_id, github_pr_number) DO UPDATE SET
  title = EXCLUDED.title, description = EXCLUDED.description,
  author_id = EXCLUDED.author_id, head_branch = EXCLUDED.head_branch,
  is_draft = EXCLUDED.is_draft, state = EXCLUDED.state,
  lines_added = EXCLUDED.lines_added, lines_deleted = EXCLUDED.lines_deleted,
  files_changed = EXCLUDED.files_changed, url = EXCLUDED.url,
  created_at = EXCLUDED.created_at, first_review_at = EXCLUDED.first_review_at,
  merged_at = EXCLUDED.merged_at, closed_at = EXCLUDED.closed_at,
  updated_at = EXCLUDED.updated_at;

-- Two CI checks per PR, with believable failures/in-progress states on open PRs.
INSERT INTO pr_checks (pr_id, name, status, url)
SELECT pr.pr_id, checks.name,
       CASE
         WHEN pr.github_pr_number = 503 AND checks.name = 'integration-tests' THEN 'FAILURE'
         WHEN pr.github_pr_number = 504 THEN 'IN_PROGRESS'
         ELSE 'SUCCESS'
       END,
       pr.url || '/checks'
FROM pull_requests pr
CROSS JOIN (VALUES ('unit-tests'), ('integration-tests'), ('code-quality')) checks(name)
WHERE pr.company_id = 12 AND pr.github_pr_id BETWEEN 120050000501 AND 120050000524
ON CONFLICT (pr_id, name) DO UPDATE SET status = EXCLUDED.status, url = EXCLUDED.url;

-- Replace only reviews belonging to this mock PR range so repeated runs stay tidy.
DELETE FROM pr_reviews
WHERE company_id = 12
  AND pr_id IN (
    SELECT pr_id FROM pull_requests
    WHERE github_pr_id BETWEEN 120050000501 AND 120050000524
  );

WITH people AS (
  SELECT user_id, row_number() OVER (ORDER BY github_id) AS rn
  FROM users WHERE company_id = 12 AND email LIKE '%.mock@devpulse.local'
)
INSERT INTO pr_reviews (company_id, pr_id, reviewer_id, review_state, reviewed_at)
SELECT 12, pr.pr_id, reviewer.user_id,
       CASE
         WHEN pr.github_pr_number = 503 THEN 'changes_requested'
         WHEN pr.state = 'open' THEN 'commented'
         ELSE 'approved'
       END,
       pr.created_at + interval '6 hours'
FROM pull_requests pr
JOIN people author_pos ON author_pos.user_id = pr.author_id
JOIN people reviewer ON reviewer.rn = (author_pos.rn % 6) + 1
WHERE pr.company_id = 12 AND pr.github_pr_id BETWEEN 120050000501 AND 120050000524;

-- Three commits per PR. Stable synthetic SHAs make this section idempotent.
INSERT INTO commits (
  commit_sha, company_id, repo_id, pr_id, author_id, message,
  commit_time, lines_added, lines_deleted
)
SELECT md5('devpulse-mock-' || pr.github_pr_number || '-' || n)::varchar(40),
       12, pr.repo_id, pr.pr_id, pr.author_id,
       CASE n WHEN 1 THEN 'feat: implement core behavior'
              WHEN 2 THEN 'test: cover edge cases'
              ELSE 'chore: address review feedback' END,
       pr.created_at + make_interval(hours => n * 3),
       greatest(5, pr.lines_added / 3), greatest(1, pr.lines_deleted / 3)
FROM pull_requests pr CROSS JOIN generate_series(1, 3) n
WHERE pr.company_id = 12 AND pr.github_pr_id BETWEEN 120050000501 AND 120050000524
ON CONFLICT (commit_sha) DO UPDATE SET
  pr_id = EXCLUDED.pr_id, author_id = EXCLUDED.author_id,
  message = EXCLUDED.message, commit_time = EXCLUDED.commit_time,
  lines_added = EXCLUDED.lines_added, lines_deleted = EXCLUDED.lines_deleted;

-- Dedicated release commits ensure every deployment has a realistic positive
-- lead time (12-47 hours from commit to production).
INSERT INTO commits (
  commit_sha, company_id, repo_id, pr_id, author_id, message,
  commit_time, lines_added, lines_deleted
)
SELECT md5('devpulse-deploy-' || n)::varchar(40), 12, r.repo_id, NULL,
       (SELECT user_id FROM users
        WHERE company_id = 12 AND email LIKE '%.mock@devpulse.local'
        ORDER BY github_id OFFSET ((n - 1) % 6) LIMIT 1),
       'release: production deployment ' || n,
       now() - make_interval(hours => n * 42 + 12 + (n % 8) * 5),
       20 + (n % 5) * 7, 4 + (n % 3) * 3
FROM generate_series(1, 24) n
CROSS JOIN (
  SELECT repo_id FROM repos
  WHERE company_id = 12 AND github_repo_id = 1200500001
) r
ON CONFLICT (commit_sha) DO UPDATE SET
  author_id = EXCLUDED.author_id, message = EXCLUDED.message,
  commit_time = EXCLUDED.commit_time,
  lines_added = EXCLUDED.lines_added, lines_deleted = EXCLUDED.lines_deleted;

-- 24 production deployments over 42 days. Every sixth deployment fails and
-- recovers in 1.5-4.5 hours, producing meaningful CFR and MTTR values.
INSERT INTO deployments (
  company_id, project_id, github_deployment_id, commit_sha, environment,
  status, deployed_at, failure_recovered_at, triggered_by_user_id
)
SELECT 12, 5, 1200501000 + n,
       md5('devpulse-deploy-' || n)::varchar(40),
       'production',
       CASE WHEN n % 6 = 0 THEN 'failed' ELSE 'success' END,
       now() - make_interval(hours => n * 42),
       CASE WHEN n % 6 = 0
            THEN now() - make_interval(hours => n * 42) + make_interval(mins => 90 + (n % 4) * 45)
       END,
       (SELECT user_id FROM users
        WHERE company_id = 12 AND email LIKE '%.mock@devpulse.local'
        ORDER BY github_id OFFSET ((n - 1) % 6) LIMIT 1)
FROM generate_series(1, 24) n
ON CONFLICT (company_id, github_deployment_id) WHERE github_deployment_id IS NOT NULL
DO UPDATE SET
  project_id = EXCLUDED.project_id, commit_sha = EXCLUDED.commit_sha,
  environment = EXCLUDED.environment, status = EXCLUDED.status,
  deployed_at = EXCLUDED.deployed_at,
  failure_recovered_at = EXCLUDED.failure_recovered_at,
  triggered_by_user_id = EXCLUDED.triggered_by_user_id;

-- Historical DORA snapshots for charting. Existing real snapshots win.
INSERT INTO dora_metrics (
  company_id, project_id, calculated_date, calculated_at, window_days,
  deployment_frequency, lead_time_hours, change_failure_rate, mttr_hours
)
SELECT 12, 5, current_date - n, now() - make_interval(days => n), 30,
       round((0.62 + ((n % 5) * 0.045))::numeric, 4),
       round((31.0 - ((n % 8) * 1.35))::numeric, 2),
       round((0.10 + ((n % 4) * 0.018))::numeric, 4),
       round((3.8 - ((n % 6) * 0.28))::numeric, 2)
FROM generate_series(0, 29) n
ON CONFLICT (project_id, calculated_date, window_days) DO NOTHING;

-- Jira work gives each developer a believable mix of completed and active work.
WITH people AS (
  SELECT user_id, row_number() OVER (ORDER BY github_id) AS rn
  FROM users WHERE company_id = 12 AND email LIKE '%.mock@devpulse.local'
)
INSERT INTO jira_issues (
  company_id, project_id, jira_key, summary, issue_type, priority,
  status, story_points, assignee_id, created_at, closed_at
)
SELECT 12, 5, 'DP-MOCK-' || n,
       (ARRAY[
         'Improve onboarding analytics', 'Investigate delayed deployments',
         'Add repository health indicator', 'Reduce noisy Slack alerts',
         'Document incident recovery flow', 'Optimize pull request queries'
       ])[((n - 1) % 6) + 1] || ' #' || n,
       (ARRAY['Story','Bug','Task'])[((n - 1) % 3) + 1],
       (ARRAY['Medium','High','Low','Medium'])[((n - 1) % 4) + 1],
       CASE WHEN n <= 12 THEN 'Done' WHEN n <= 20 THEN 'In Progress' ELSE 'To Do' END,
       (ARRAY[2,3,5,8])[((n - 1) % 4) + 1], p.user_id,
       now() - make_interval(days => 40 - n),
       CASE WHEN n <= 12 THEN now() - make_interval(days => 24 - n) END
FROM generate_series(1, 26) n
JOIN people p ON p.rn = ((n - 1) % 6) + 1
ON CONFLICT (company_id, jira_key) DO UPDATE SET
  project_id = EXCLUDED.project_id, summary = EXCLUDED.summary,
  issue_type = EXCLUDED.issue_type, priority = EXCLUDED.priority,
  status = EXCLUDED.status, story_points = EXCLUDED.story_points,
  assignee_id = EXCLUDED.assignee_id, created_at = EXCLUDED.created_at,
  closed_at = EXCLUDED.closed_at;

-- ML risk scores for the open PRs.
DELETE FROM pr_predictions
WHERE company_id = 12
  AND pr_id IN (
    SELECT pr_id FROM pull_requests
    WHERE github_pr_id BETWEEN 120050000501 AND 120050000506
  );

INSERT INTO pr_predictions (
  company_id, pr_id, algorithm, model_version, prediction_type,
  risk_category, risk_score, confidence, predicted_at
)
SELECT 12, pr.pr_id, 'xgboost', '2.1.0', 'stale_risk',
       CASE WHEN pr.github_pr_number IN (503, 505) THEN 'high'
            WHEN pr.github_pr_number IN (501, 504) THEN 'medium'
            ELSE 'low' END,
       CASE WHEN pr.github_pr_number = 503 THEN 0.87
            WHEN pr.github_pr_number = 505 THEN 0.79
            WHEN pr.github_pr_number IN (501, 504) THEN 0.55
            ELSE 0.22 END,
       0.91, now() - interval '1 hour'
FROM pull_requests pr
WHERE pr.company_id = 12 AND pr.github_pr_id BETWEEN 120050000501 AND 120050000506;

-- Alert examples for dashboard notification panels.
INSERT INTO alert_rules (
  company_id, project_id, rule_type, threshold_hours, slack_channel,
  is_active, created_by_user_id, created_at
)
SELECT 12, 5, 'stale_pull_request', 48, '#dev-alerts', true,
       u.user_id, now() - interval '90 days'
FROM users u
WHERE u.email = 'maya.perera.mock@devpulse.local'
  AND NOT EXISTS (
    SELECT 1 FROM alert_rules
    WHERE company_id = 12 AND project_id = 5
      AND rule_type = 'stale_pull_request' AND slack_channel = '#dev-alerts'
  );

INSERT INTO alerts (
  company_id, project_id, rule_id, entity_type, entity_id,
  severity, message, triggered_at, resolved_at
)
SELECT 12, 5, ar.rule_id, 'pull_request', pr.pr_id,
       CASE WHEN pr.github_pr_number = 503 THEN 'critical' ELSE 'warning' END,
       CASE WHEN pr.github_pr_number = 503
            THEN 'PR #503 has failing integration tests and no approval.'
            ELSE 'PR #505 has been open for two weeks and needs review.' END,
       now() - CASE WHEN pr.github_pr_number = 503 THEN interval '3 hours' ELSE interval '1 day' END,
       NULL
FROM alert_rules ar
JOIN pull_requests pr ON pr.github_pr_number IN (503, 505)
JOIN repos r ON r.repo_id = pr.repo_id AND r.github_repo_id = 1200500001
WHERE ar.company_id = 12 AND ar.project_id = 5
  AND ar.rule_type = 'stale_pull_request' AND ar.slack_channel = '#dev-alerts'
  AND NOT EXISTS (
    SELECT 1 FROM alerts a
    WHERE a.company_id = 12 AND a.project_id = 5
      AND a.entity_type = 'pull_request' AND a.entity_id = pr.pr_id
      AND a.resolved_at IS NULL
  );

COMMIT;

-- Quick verification summary.
SELECT 'developers' AS dataset, count(*) AS rows
FROM project_members pm JOIN users u ON u.user_id = pm.user_id
WHERE pm.project_id = 5 AND u.company_id = 12
UNION ALL
SELECT 'pull_requests', count(*) FROM pull_requests pr
JOIN repos r ON r.repo_id = pr.repo_id
WHERE pr.company_id = 12 AND r.project_id = 5
UNION ALL
SELECT 'deployments', count(*) FROM deployments
WHERE company_id = 12 AND project_id = 5
UNION ALL
SELECT 'jira_issues', count(*) FROM jira_issues
WHERE company_id = 12 AND project_id = 5
UNION ALL
SELECT 'dora_history', count(*) FROM dora_metrics
WHERE company_id = 12 AND project_id = 5 AND window_days = 30;
