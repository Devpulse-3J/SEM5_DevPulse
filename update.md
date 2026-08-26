# Update — integration-service & notification-service

Status after the security/correctness pass. Both services build and all tests
pass: **integration-service 31**, **notification-service 26**, 0 failures.

---

## integration-service (port 8082)

### What it does

The **ingestion edge**. It is the only service that talks to GitHub and Jira.
It receives webhooks, proves they are genuine, stores the raw payload, upserts
the domain rows it owns, converts the vendor-specific JSON into a canonical
DevPulse event, and publishes that event to RabbitMQ. Nothing downstream ever
sees a raw GitHub payload.

### Endpoints

| Method | Path (service) | Via gateway | Auth |
|---|---|---|---|
| POST | `/webhooks/github` | `/api/webhooks/github` | HMAC (public to JWT) |
| POST | `/webhooks/jira` | `/api/webhooks/jira` | HMAC (public to JWT) |
| POST | `/integrations/github/sync` | `/api/integrations/github/sync` | JWT |

`/api/webhooks/**` is in the gateway's `PUBLIC_PATH_PREFIXES` — GitHub and Jira
cannot present a JWT, so the HMAC signature **is** the authentication.

### External APIs it connects to

- **GitHub webhooks** (inbound) — `X-GitHub-Event`, `X-Hub-Signature-256`
- **Jira webhooks** (inbound) — `X-Jira-Event`, `X-Jira-Signature`
- **GitHub REST API** (outbound, `https://api.github.com`) for historical backfill:
  - `GET /repos/{owner}/{repo}`
  - `GET /repos/{owner}/{repo}/pulls?state=&per_page=100`
  - `GET /repos/{owner}/{repo}/commits?per_page=100`
  - `GET /repos/{owner}/{repo}/issues?state=&per_page=100`

### Tables it owns (writes)

`repos`, `jira_issues`, `raw_event_log`

### Events it publishes

Exchange `devpulse.events` (topic, durable). Routing key = the event's own
`eventType`:

`pr.opened` · `pr.merged` · `pr.closed` · `commit.pushed` ·
`deployment.created` · `issue.updated`

### What changed in this pass

- 🔴 **HMAC is now mandatory.** Both handlers previously ran
  `if (signature != null && !valid)` — a request with **no** signature header
  skipped verification entirely. On a public path that meant anyone could forge
  webhooks, write to `raw_event_log`, and inject events onto RabbitMQ. Missing
  or blank signatures now return 401 before any DB write.
- 🔴 **Committed GitHub credentials removed.** `application.yml` carried a real
  `client-secret`, `client-id` and `app.id` as literal defaults.
  `GITHUB_WEBHOOK_SECRET` now has **no default** — the service fails to start
  rather than validating signatures against a value published in the repo.
- 🔴 **`GithubSyncController` was unreachable.** It mapped
  `/api/integrations/github`, but the gateway applies `StripPrefix=1` and
  forwards `/integrations/github/...` — a guaranteed 404. Prefix removed.
- 🔵 **`/webhooks/test-high-risk-alert` deleted.** A publicly callable endpoint
  that published fake `alert.pr_high_risk` events with hardcoded values.

---

## notification-service (port 8084)

### What it does

The **delivery end**. It consumes events from RabbitMQ, decides who should hear
about them by evaluating rows in `alert_rules`, records the alert, then fans the
message out over Slack, email, and outbound webhooks — logging the outcome of
every attempt. It also serves CRUD for the alert rules themselves and the Slack
OAuth install flow.

### Endpoints

| Method | Path (service) | Via gateway | Auth |
|---|---|---|---|
| GET | `/alerts/rules` | `/api/alerts/rules` | JWT |
| GET | `/alerts/rules/{id}` | `/api/alerts/rules/{id}` | JWT |
| POST | `/alerts/rules` | `/api/alerts/rules` | JWT |
| DELETE | `/alerts/rules/{id}` | `/api/alerts/rules/{id}` | JWT |
| GET | `/slack/oauth/install` | `/api/slack/oauth/install` | ⚠️ **no gateway route** |
| GET | `/slack/oauth/callback` | `/api/slack/oauth/callback` | ⚠️ **no gateway route** |
| GET | `/slack/channels` | `/api/slack/channels` | ⚠️ **no gateway route** |

### External APIs it connects to

- **Slack Web API** — `oauth/v2/authorize`, `api/oauth.v2.access`,
  `api/chat.postMessage`, `api/conversations.list`
- **Slack Incoming Webhooks** — when a webhook URL is configured instead of a bot token
- **SMTP** — via `JavaMailSender`
- **Arbitrary outbound webhooks** — customer-supplied URLs

### Tables it owns (writes)

`alert_rules`, `alerts`, `notifications`

### Events it consumes

Queue `notification.events` (durable), bound to exchange `devpulse.events` with
**`alert.#`** and **`pr.#`**.

> `commit.pushed`, `deployment.created` and `issue.updated` are published by
> integration-service but bound by nobody. They are silently discarded today.

### What changed in this pass

- 🔴 **Cross-tenant data leak closed.** `getRuleById` and `deleteRule` used a
  bare `findById(ruleId)` — any authenticated user could read or delete another
  company's alert rule by guessing a number. Both now use
  `findByRuleIdAndCompanyId`, and a foreign rule returns **404, not 403**, so
  the id is never confirmed.
- 🔴 **`companyId` is no longer client-controlled.** It was
  `@RequestParam(defaultValue = "1")` — `?companyId=7` was enough to read
  another tenant. It now comes from the `X-Company-Id` header, which the gateway
  strips from the incoming request and re-adds from validated JWT claims.
  `createRule` also overwrites any `companyId` in the request body.
- 🔴 **Slack token no longer shared between tenants.** `botToken` was a
  **mutable field on a singleton bean**, assigned during the OAuth callback —
  whichever company installed last silently overwrote every other company's
  token, and it was lost on restart. The field is `final` again, and the
  callback no longer returns the token in its HTTP response body.
- 🔴 **Slack now fails loudly.** With no bot token and no webhook URL, the
  service logged *"Simulating successful delivery"* and returned `true`. Every
  row in `notifications` was written as `sent` for a message that never left the
  process. It now returns `false` and logs an error.
- 🟡 **`SlackOAuthController`** mapped `/api/slack` — same `StripPrefix`
  problem as above. Now `/slack`.

---

## RabbitMQ flow (end to end)

```
GitHub / Jira ──HMAC──▶ integration-service
                             │  publishes to exchange `devpulse.events` (topic)
                             │  routing key = eventType
                             ▼
              ┌──────────────────────────────┐
              │   exchange devpulse.events   │
              └──────────────────────────────┘
                    │ alert.#        │ pr.#          ✗ commit.pushed
                    │                │               ✗ deployment.created
                    ▼                ▼               ✗ issue.updated
              queue `notification.events` ──▶ NotificationEventListener
                                                 ├─ evaluate alert_rules
                                                 ├─ insert alerts
                                                 └─ Slack / email / webhook
                                                    → insert notifications
```

Both services declare the exchange identically (durable, non-auto-delete) and
both use `Jackson2JsonMessageConverter`, so serialisation is consistent.

---

## TODO

### Blocking / security

- [ ] **Rotate the GitHub App client secret.** `1df4005d…` is in git history at
      commit `d8187f6`. Deleting it from the file does **not** remove it from
      history — rotate it in the GitHub App settings.
- [ ] **Add the gateway route for `/api/slack/**`.** `SlackOAuthController` now
      maps `/slack`, but nothing routes to it, so the whole Slack install flow is
      still unreachable. One predicate in
      `api-gateway/src/main/resources/application.yml`:
      `Path=/api/alerts/**,/api/notifications/**,/api/slack/**`
- [ ] **Persist Slack tokens per company.** They belong in the `integrations`
      table keyed by company — but **auth-service owns that table**, so
      notification-service may only read it. Needs a cross-team decision:
      either auth-service exposes a write endpoint, or ownership moves.
      Until then, OAuth completes but the token is discarded.
- [ ] **Remove the `JWT_SECRET` default in auth-service.** It falls back to
      `devpulse-default-secret-key-change-me-…`, so with `JWT_SECRET` unset
      auth-service signs tokens with a publicly known key while the gateway
      (no default) refuses to start. *Out of scope for this pass.*

### Correctness

- [ ] **Return 200 before processing.** Both webhook handlers currently do
      raw-save → DB upsert → normalize → publish, *then* respond. GitHub times
      out around 10s. Needs a new `@Service` with an `@Async` method plus
      `@EnableAsync` — `@Async` does not work on self-invocation.
- [ ] **`EmailNotificationService` has the same fake-success bug Slack had.**
      With no `JavaMailSender` configured it logs *"Simulating successful email
      delivery"* and returns `true`. Not fixed in this pass.
- [ ] **Webhook dispatch is called with a `null` URL** and logged to
      `notifications` under channel `in_app` to satisfy a CHECK constraint. The
      recorded channel does not describe what was attempted.
- [ ] **Decide who consumes `commit.pushed` / `deployment.created` /
      `issue.updated`.** Presumably metrics-service. Today they are published
      and discarded.
- [ ] **Webhook tenant identity.** `X-Company-Id` defaults to `1` on the webhook
      handlers, and since those paths are public the gateway never populates it —
      so every webhook lands in company 1. Needs a real mapping, e.g. repo →
      company via the `repos` table.

### Hygiene

- [ ] Hardcoded `#dev-alerts` fallback (`NotificationEventListener`, `SlackNotificationService`)
- [ ] Hardcoded `alerts@devpulse.com` recipient (`NotificationEventListener`)
- [ ] No `devpulse.notification.slack.*` keys in `notification-service/application.yml`
      at all — Slack is unconfigured by default, which now surfaces as a logged failure
- [ ] No test covers the Slack OAuth callback path against a real token exchange

---

## Commands

```bash
cd backend

mvn -pl integration-service,notification-service -am test    # 57 tests
mvn -pl notification-service test -Dtest=AlertRuleServiceTest

curl http://localhost:8082/actuator/health    # integration-service
curl http://localhost:8084/actuator/health    # notification-service
```
