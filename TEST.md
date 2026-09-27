# DevPulse Backend — Test Inventory

Generated from the codebase on `main` (commit `853d56d`), 2026-09-27. This lists every
automated test file in the backend, for building the project's test report. Counts are
test **cases** (a parameterized test counts each of its inputs separately); everything
below passes as of this commit (`mvn test` and `pytest`, run in full).

| Language | Test files | Test cases |
|---|---|---|
| Java (6 modules) | 40 | 243 |
| Python (analytics-service) | 2 | 30 |
| **Total** | **42** | **273** |

---

## shared-contracts

| File | Tests | Covers |
|---|---|---|
| `com.devpulse.contracts.events.EventSerializationTest` | 7 | JSON round-tripping of the shared event DTOs (`PrOpenedEvent`, `CommitPushedEvent`, `IssueUpdatedEvent`, etc.) published/consumed over RabbitMQ. |

**Subtotal: 7**

---

## api-gateway

| File | Tests | Covers |
|---|---|---|
| `com.devpulse.gateway.filter.JwtAuthenticationFilterTest` | 6 | JWT validation at the edge: valid/expired/malformed tokens, public-path bypass, `X-User-Id`/`X-Company-Id` header stripping and re-injection. |
| `com.devpulse.gateway.ApiGatewayApplicationTests` | 1 | Spring context loads with the real security/routing config. |

**Subtotal: 7**

---

## auth-service

| File | Tests | Covers |
|---|---|---|
| `com.devpulse.auth.controller.AuthControllerTest` | 16 | `/auth/register`, `/auth/login`, `/auth/me` (including company-scoped profile via `X-Company-Id`), `/auth/me/github` link + lookup endpoints — full Spring Security filter chain via `@SpringBootTest` + MockMvc. |
| `com.devpulse.auth.controller.WorkspaceInviteControllerTest` | 3 | Organization invite/join-request HTTP endpoints. |
| `com.devpulse.auth.security.JwtServiceTest` | 4 | Token issuing for a user's home company vs. an explicit switched company/role; claim contents. |
| `com.devpulse.auth.security.ProjectAccessServiceTest` | 11 | Tenancy + role checks resolved via `company_members` (not `users.company_id`); regression test proving an admin's privilege does not leak into a second company they only have a member role in. |
| `com.devpulse.auth.service.AuthServiceRegisterTest` | 22 | Registration (new company/admin, join-by-id, project-invite token, legacy placeholder claim), plus nested `SwitchCompany` (4) and `Profile` (6) groups covering `/auth/companies/{id}/switch` and the company-aware `/auth/me` response. |
| `com.devpulse.auth.service.GithubIdentityServiceTest` | 10 | Linking a GitHub username (saves the numeric id, refuses an account already linked elsewhere) and the lookup-only preview used for user confirmation before linking. |
| `com.devpulse.auth.service.ProjectInvitationClaimServiceTest` | 13 | Accepting an emailed project invitation: token validity, expiry, email match, company/role assignment. |
| `com.devpulse.auth.service.ProjectMemberInviteTest` | 12 | Inviting an existing user (including cross-company — a user from another company gains a `company_members` row without losing their home company) vs. an unregistered email (pending `project_invitations` row). |
| `com.devpulse.auth.service.WorkspaceInviteServiceTest` | 5 | Organization-level invite/accept/join-request flow, including its `company_members` dual-write. |
| `com.devpulse.auth.AuthServiceApplicationTests` | 1 | Spring context loads. |

**Subtotal: 97**

---

## integration-service

| File | Tests | Covers |
|---|---|---|
| `com.devpulse.integration.client.GithubApiClientTest` | 3 | GitHub REST client wrapper. |
| `com.devpulse.integration.config.RabbitMQConfigTest` | 2 | Exchange/queue/binding declarations. |
| `com.devpulse.integration.controller.GithubSyncControllerTest` | 1 | Manual GitHub sync trigger endpoint. |
| `com.devpulse.integration.controller.RepositoriesControllerTest` | 7 | Repo linking/listing endpoints, GitHub App installation checks. |
| `com.devpulse.integration.controller.WebhookControllerTest` | 7 | Inbound webhook HTTP handling: signature checks, provider routing, raw-event persistence. |
| `com.devpulse.integration.entity.EntityTest` | 3 | JPA entity mapping sanity checks. |
| `com.devpulse.integration.github.GithubSignatureValidatorTest` | 3 | GitHub webhook HMAC signature validation. |
| `com.devpulse.integration.jira.JiraSignatureValidatorTest` | 3 | Jira/Atlassian webhook signature validation. |
| `com.devpulse.integration.service.EventPublisherServiceTest` | 2 | Publishing normalized events to RabbitMQ. |
| `com.devpulse.integration.service.GithubHistoricalSyncServiceTest` | 4 | Backfilling PRs/commits for a newly linked repo, including that a synced commit keeps GitHub's real committer/author date (not the sync's run time) and that a missing author id stays unknown rather than defaulting to user 1. |
| `com.devpulse.integration.service.WebhookEventNormalizerTest` | 21 | Raw GitHub/Jira payload → shared event DTOs: PR opened/closed/merged, push, deployment/deployment_status, workflow_job fallback, Jira issue updates. Includes regression tests that a missing/non-numeric/oversized author, pusher or Jira assignee id becomes `null` (not `1`), and that a pushed commit keeps GitHub's own timestamp. |

**Subtotal: 56**

---

## metrics-service

| File | Tests | Covers |
|---|---|---|
| `com.devpulse.metrics.controller.MetricsControllerTest` | 3 | `/metrics/review-velocity`, `/metrics/devex`, and the manual snapshot-trigger endpoint. |
| `com.devpulse.metrics.security.ProjectAccessServiceTest` | 5 | Company/project read-access checks, plus the admin-only check gating the DORA snapshot rebuild endpoint. |
| `com.devpulse.metrics.security.RequestContextResolverTest` | 3 | `X-User-Id`/`X-Company-Id` header parsing. |
| `com.devpulse.metrics.service.ActivityMetricsServicePullRequestsTest` | 8 | `GET /metrics/prs`, including `myPrs=true` server-side author filtering and the PR risk-prediction join. |
| `com.devpulse.metrics.service.ActivityMetricsServiceTest` | 2 | Team workload/activity aggregation. |
| `com.devpulse.metrics.service.AuthorRelinkServiceTest` | 4 | `POST /metrics/authors/relink` — attributing a user's earlier unauthored PRs to them once their GitHub account is linked; scoping to the caller only. |
| `com.devpulse.metrics.service.calculation.DoraCalculatorsTest` | 12 | Deployment frequency, lead time, change failure rate and MTTR calculators, including MTTR deriving recovery from the next successful deployment when no explicit recovery timestamp exists, and order-independence. |
| `com.devpulse.metrics.service.DevExMetricsTest` | 2 | Developer-experience/workload balance metric computation. |
| `com.devpulse.metrics.service.DoraMetricsServiceRebuildTest` | 3 | `POST /metrics/dora/snapshots/rebuild` — recalculating each past day's stored snapshot from only the data that existed at that day's cutoff (00:05 UTC), admin-only. |
| `com.devpulse.metrics.service.DoraSnapshotSchedulerTest` | 3 | The nightly snapshot job: all projects processed, one project's failure doesn't block the rest, manual trigger filtering. |
| `com.devpulse.metrics.service.MetricEventIngestionServiceTest` | 2 | RabbitMQ event → `pull_requests`/`commits`/`deployments` row persistence. |
| `com.devpulse.metrics.service.ReviewVelocityServiceTest` | 2 | Time-to-first-review, review turnaround and iteration-count metrics. |

**Subtotal: 49**

---

## notification-service

| File | Tests | Covers |
|---|---|---|
| `com.devpulse.notification.config.RabbitMQConfigTest` | 1 | Queue/exchange declarations. |
| `com.devpulse.notification.controller.AlertRuleControllerTest` | 6 | Alert rule CRUD endpoints. |
| `com.devpulse.notification.email.EmailNotificationServiceTest` | 1 | SMTP email delivery. |
| `com.devpulse.notification.entity.NotificationEntityTest` | 3 | JPA entity mapping. |
| `com.devpulse.notification.service.AlertRuleServiceTest` | 6 | Alert rule matching/triggering logic. |
| `com.devpulse.notification.service.NotificationEventListenerTest` | 2 | Consuming `alert.pr_high_risk` and related events off RabbitMQ. |
| `com.devpulse.notification.slack.SlackNotificationServiceTest` | 4 | Slack delivery via bot token / webhook, reporting failure (not false success) when neither is configured; the `devpulse.notification.slack.*` → `SLACK_*` env-var binding guard added after the 502 bug. |
| `com.devpulse.notification.slack.SlackOAuthControllerTest` | 3 | Slack OAuth install/callback, channel listing. |
| `com.devpulse.notification.webhook.WebhookNotificationServiceTest` | 1 | Generic outbound webhook delivery. |

**Subtotal: 27**

---

## analytics-service (Python)

| File | Tests | Covers |
|---|---|---|
| `tests/test_feature_extractor.py` | 19 | ML feature extraction for PR risk scoring: timezone handling, author/repo history windows, empty-body/no-history sentinels, tenant isolation (a PR never sees another company's data). |
| `tests/test_pr_events_consumer.py` | 11 | RabbitMQ `pr.opened` consumer: GitHub-id → `pull_requests.pr_id` lookup, risk-alert publishing, and the reconnect-with-backoff fix (retries indefinitely on a startup or mid-run RabbitMQ connection failure instead of the thread dying silently — the bug that caused a 232-message backlog). |

**Subtotal: 30**

---

## Notes for the report

- **No test files exist for:** `WorkspaceInviteControllerTest`'s sibling admin-console endpoints beyond what's listed, and there is no dedicated test for `AuthorRelinkController`/`GithubIdentityController` at the HTTP layer beyond what's folded into `AuthControllerTest` — the service layer underneath both is fully covered.
- **Live/manual verification done but not automated** (documented in conversation history, not as test files): end-to-end multi-company invite + switch-company flow against a real deployed environment with real Supabase data (cleaned up afterward); the GitHub webhook → RabbitMQ → metrics-service → DORA pipeline verified with real deployment data; the corrected commit-time backfill (150 rows) verified before/after in a guarded transaction.
- **Planned, not yet done:** System Usability Scale (SUS) testing — see the separate test-plan document for methodology and target participants.
