# Service Reference

What each service is *for*, and what breaks if you delete it. This is the orientation
document — read it before picking up your first ticket. For the folder layout and build
instructions, see the [README](../README.md).

> **Database note:** all backend services share **one** PostgreSQL database (`devpulse`); no
> service owns its own database. Each service logically *owns* specific tables — it may read any
> table but writes only its own. See [Shared database & table ownership](#shared-database--table-ownership).

**At a glance**

| Service | Runtime | Port | Connects to DB? | One-line purpose |
|---|---|---|---|---|
| [api-gateway](#api-gateway) | Java · Spring Cloud Gateway | 8080 | No | The front door — the only publicly reachable process |
| [auth-service](#auth-service) | Java · Spring Boot | 8081 | Yes | Who you are, and what you're allowed to touch |
| [integration-service](#integration-service) | Java · Spring Boot | 8082 | Yes | Translates GitHub and Jira webhooks into internal events |
| [metrics-service](#metrics-service) | Java · Spring Boot | 8083 | Yes | The system of record for what actually happened |
| [analytics-service](#analytics-service) | Python · FastAPI | 8000 | Yes | The prediction layer — the ML component |
| [notification-service](#notification-service) | Java · Spring Boot | 8084 | Yes | Getting a human's attention |
| [shared-contracts](#shared-contracts--not-a-service) | — | — | No | The written-down agreement between services |
| [dashboard-app](#the-two-frontends) | Next.js | 3000 | No | Daily UI for Managers and Developers |
| [admin-app](#the-two-frontends) | Next.js | 3001 | No | Occasional UI for company Admins |

---

## api-gateway

**The front door.** Every browser request enters here and nowhere else.

It exists so the other five services can stop worrying about the internet. It checks the JWT
once at the edge, rate-limits abusive callers via Redis, and forwards `/api/metrics/**` to
metrics, `/api/auth/**` to auth, and so on. Without it you would expose five services publicly
and duplicate auth logic in all of them.

Owns no database. Holds no state.

---

## auth-service

**Who you are, and what you're allowed to touch.**

Owns users, companies, projects, and — critically — the membership table that answers "what
role does *this* person have on *this* project?" It issues the JWTs everything else trusts.

This is the one service everything depends on. It is also the widest in scope, since projects
and companies live here too.

---

## integration-service

**The translator at the border.**

GitHub and Jira each send their own webhook format. This service receives them, verifies the
signature is genuinely from GitHub (not someone forging events), and converts wildly different
payloads into one internal shape — `pr.opened`, `commit.pushed` — then drops them on the bus.

It exists so no other service ever has to know what GitHub's JSON looks like. Change providers,
and only this service changes.

---

## metrics-service

**The system of record for what actually happened.**

Consumes those normalised events and stores PRs, commits, and deployments. From that history it
computes the four DORA metrics — deployment frequency, lead time, MTTR, change failure rate.

This is where "how fast does this team ship?" gets answered. It is the main thing the dashboard
reads.

---

## analytics-service

**The prediction layer — the ML component.**

Consumes the same PR events, but instead of recording what happened it guesses what *will*
happen: is this PR going stale, is it risky to merge. Scores get stored; anything above
`RISK_THRESHOLD` gets published as an alert.

Python rather than Java purely because scikit-learn and XGBoost live there. There is no separate
"ML service" — this is it.

---

## notification-service

**Getting a human's attention.**

Holds each project's alert rules and channels. When a high-risk alert appears on the bus, it
decides who cares and delivers to Slack, email, or a custom webhook — then records whether
delivery succeeded.

It exists so that no other service needs Slack credentials or SMTP config.

---

## shared-contracts — not a service

**The written-down agreement.** Defines the event shapes and DTOs so all six services mean the
same thing by `pr.opened`. Never runs, no port, no database.

Its TypeScript twin is `frontend/shared-types/`. Update both together, or the frontend and
backend silently drift apart.

---



## Shared database & table ownership

All backend services connect to **one shared PostgreSQL database** (`devpulse`). This is
deliberate for a small team — simpler to run, and it lets foreign keys cross service boundaries
(`pull_requests → users`, `dora_metrics → projects`). PostgreSQL is infrastructure, not a service.

The discipline that keeps this "microservices" and not a big ball of mud:

> **A service READS any table it needs, but WRITES only the tables it owns.**

| Service | Owns (writes) |
|---|---|
| auth-service | companies, users, projects, project_members, integrations |
| integration-service | repos, jira_issues, raw_event_log |
| metrics-service | pull_requests, pr_reviews, commits, deployments, dora_metrics |
| analytics-service | pr_predictions |
| notification-service | alert_rules, alerts, notifications |

To change data it doesn't own, a service calls the owner over REST or reacts to an event — it
never writes another service's tables. `api-gateway` holds no DB connection at all.

## Migrations

The schema is owned centrally by [`backend/database/`](../backend/database/README.md). There is
**one** Flyway migration owner; individual services carry no migrations and run with
`spring.flyway.enabled=false`. Add a schema change as a new `V<n>__*.sql` in
`backend/database/migrations/` — never inside a service.

## The pattern underneath

Notice how three services all consume `pr.opened` and none of them know about each other:

| Service | Same event, different job |
|---|---|
| metrics-service | records it as history |
| analytics-service | predicts risk from it |
| notification-service | alerts a human about it |

That is the whole point of the message bus. Adding a fourth consumer later means writing one new
service — you touch nothing that already works.

---

## Known rough edge

`auth-service` owning users, companies, **and** projects is a lot for one service. In a larger
system, projects would likely be their own service. For a three-person semester project, keeping
them together is the right call — just be aware it is the piece most likely to feel crowded, and
the one most likely to need splitting if scope grows.
