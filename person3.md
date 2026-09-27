# DevPulse Test Report — Person 3 Sections

Scope: this file covers **only** the sections owned by Person 3 in the work-separation
table — §3.3, §3.4, §3.8, and §5 (SUS). §1, §2, §3 (intro), §3.1, §3.2, §3.5, §3.6, §3.7,
§4, §6 and §7 belong to Person 1 / Person 2 / Shared and are not duplicated here.

Numbering below matches the shared report outline exactly, so these sections can be
copied straight in.

---

## 3.3 User Interface Testing

| | |
|---|---|
| **Technique Objective** | Verify that the Next.js frontend renders correctly, routes each user to the right screen for their role and company context, and that every interactive form behaves correctly across the three roles the system defines (Admin, Manager, Developer). |
| **Technique** | Manual exploratory and scripted UI walkthroughs of each role's primary screens — login/register, the project picker, the Manager dashboard, the Developer dashboard, the DORA page, the PR list, alerts, and the admin console — run against both the deployed instance (`https://odineye.cse23.org`) and a local `npm run dev` instance, across the three role types and both login entry points (`/login` for the workspace, `/adminlogin` for the admin console). |
| **Oracles** | The role-based landing rules in `src/lib/redirect.ts` (`memberLandingPath`, `adminLandingPath`, `loginPathFor`), cross-checked against their own automated unit tests (`redirect.test.ts`) as the source of truth for expected behaviour; the `/auth/me` API response shape as the oracle for what a screen should be able to display; project UI-copy expectations (e.g. the header should read `<Company> / <Project>`, a role badge should read `MANAGER`/`DEVELOPER`). |
| **Required Tools** | Chrome, Firefox and Microsoft Edge (desktop); Chrome DevTools device emulation for a basic mobile-width pass; test accounts covering Admin, Manager and Developer roles across at least two companies (`fcode`, `Odin_eye`) so the cross-company project-picker flow is actually exercised, not just the single-company case. |
| **Success Criteria** | Every primary screen renders with no console errors or broken layout at desktop width in all three browsers; a user always lands on the correct screen for the door they logged in through and their role; a form's client-side validation messages agree with the server's rejections (no silent form failures); switching company from the project picker updates the header and sidebar without a manual page refresh. |
| **Special Considerations** | `/login` (workspace) and `/adminlogin` (admin console) are two separate front doors that must never cross-redirect a user into the wrong one — this was a real bug fixed earlier in the project and is now covered by automated tests, but each release still needs one manual pass per door. The GitHub "Look up → confirm → link" card and the admin-only "Rebuild history" button are conditionally rendered by role/company, so they must be checked with more than one test account, not just the primary admin. |

---

## 3.4 Performance Profiling

| | |
|---|---|
| **Technique Objective** | Establish a baseline for how quickly each backend service responds under normal load, and identify the components most likely to become bottlenecks — the heaviest DB queries, the DORA calculation path, and PR risk-scoring latency — given the whole system runs on a single, resource-constrained EC2 instance. |
| **Technique** | Manual timing of key request paths — `GET /api/metrics/dora`, `GET /api/metrics/prs`, `GET /api/auth/me` — measured with the browser Network tab and `curl -w "%{time_total}"`; timing the DORA snapshot-rebuild endpoint (`POST /metrics/dora/snapshots/rebuild`) over a realistic 30–90 day window; timing end-to-end latency from a GitHub `pr.opened` webhook to a risk score appearing in `pr_predictions`. |
| **Oracles** | A qualitative target that interactive dashboard actions should feel instant (sub-1-second for a simple read), informed by the rating bands already defined in `DoraRatingPolicy`; the observed baseline JVM cold-start times for each service (roughly 30–45 seconds) as a reference for what counts as "slow" on this hardware. There is no formal SLA, so most comparisons are before/after a change rather than against an absolute number. |
| **Required Tools** | Browser DevTools Network panel; `curl` with `-w` for response-time formatting; SSH access to the EC2 instance for `docker stats` (per-container CPU/memory) and `free -h` during a profiling run; the RabbitMQ management UI for queue depth as a proxy for consumer processing speed. |
| **Success Criteria** | Dashboard reads (DORA summary, PR list) return in under 1 second under normal load; the snapshot-rebuild endpoint completes for a 30-day window without timing out or raising memory enough to risk an OOM kill; the PR-risk pipeline (webhook → RabbitMQ → analytics-service → `pr_predictions`) completes within a few seconds when the queue is not backlogged. |
| **Special Considerations** | All 5 Java services plus RabbitMQ, Redis and the Python analytics-service share one EC2 instance (~7.5 GB RAM) with per-service JVM heap caps (e.g. `-Xmx384m`), so profiling has to account for shared capacity rather than assuming each service has room to itself — the host has been observed swapping under combined load during this project. PR risk scoring is asynchronous over RabbitMQ, so its latency must never be counted as part of the webhook's HTTP response time — that request returns immediately regardless of how long scoring takes afterward. |

---

## 3.8 Configuration Testing

| | |
|---|---|
| **Technique Objective** | Confirm the application behaves correctly across every environment it has to run in — a developer's laptop, the shared Supabase database, and the single production EC2 instance — and that every configuration value actually reaches the code that reads it, not just the container it's set in. |
| **Technique** | Manual verification of environment-variable propagation from `.env` files through Docker Compose to each service's `application.yml` binding, repeated whenever a compose file or a service's configuration properties change; comparing a local run (`docker-compose.yml`, Postgres in a container) against production (`docker-compose.supabase.yml` override, pooled Supabase connection); a fresh-clone "does it start" check to simulate a new teammate's machine. |
| **Oracles** | Each service's own `application.yml` as the source of truth for which environment variable a property is bound to (e.g. `devpulse.notification.slack.webhook-url` ← `SLACK_WEBHOOK_URL`); the `.env.local.example` files as the documented contract for what an environment must supply; the container's own startup log as proof a value was actually read, not merely present in the shell. |
| **Required Tools** | Docker and Docker Compose — the two compose files are always run together, the Supabase override is never used alone; SSH access to the EC2 instance to inspect the live `.env` and each container's resolved environment (`docker exec <service> printenv`); a local machine with a fresh clone to test the "copy every `.env.local.example` to `.env.local`" onboarding step. |
| **Success Criteria** | A variable set in `.env.local.example` and its production `.env` counterpart is demonstrably read by the service that needs it, confirmed by observing the expected behaviour rather than just seeing the variable in the environment; the two compose files together produce a working local stack; production starts cleanly after a deploy with no missing-variable errors in any service's log. |
| **Special Considerations** | A variable can exist in the deployment's `.env` and still do nothing if the owning service's `application.yml` never binds it — this was the exact cause of a real bug in this project (Slack sends silently failing because `SLACK_WEBHOOK_URL` reached the container but was never bound to the property the code reads, surfacing as a 502 from the team-message endpoint). It is now the standing configuration-testing checklist item: does `.env` → `docker-compose.yml` → `application.yml` → `@Value` form one unbroken chain for every new variable? A second real example was the Jira OAuth redirect URI defaulting to `http://localhost:8080/...` in production until corrected — a reminder that a convenient local default can mask a missing production override. CI runs on a single platform (`ubuntu-latest`); genuine cross-OS coverage is limited to what the three team members' own laptops provide. |

---

## 5. System Usability Scale (SUS) Testing — Planned

Not yet conducted at time of writing; scheduled once the current feature set (multi-company
support, GitHub-linked PR attribution, DORA history) is stable and deployed.

### 5.1 Methodology

- Standard 10-item SUS questionnaire, each item scored 1 (Strongly Disagree) to 5 (Strongly
  Agree), alternating positively- and negatively-worded statements.
- **Scoring:** for odd-numbered items, score = response − 1; for even-numbered items,
  score = 5 − response; sum all ten scores and multiply by 2.5 to get a 0–100 score per
  participant. The overall SUS score is the average across all participants.
- **Target benchmark:** a score above 68 (the published industry-average SUS score) is
  treated as a pass. Scores are also mapped onto the standard adjective/grade scale (e.g.
  "Good", "Excellent") for the final report.
- The questionnaire is administered immediately after each participant finishes the test
  tasks, without seeing the questions beforehand, to avoid biasing their answers mid-task.

### 5.2 Participants

- **10 participants**, one drawn from each of **ten other semester-project teams** in the
  course — deliberately not from our own team, to avoid familiarity bias.
- Each participant plays the role of either a Developer or an Engineering Manager and has
  no prior exposure to DevPulse's interface.

### 5.3 Flows to Be Tested / Timing

Each participant works through the same task list, moderated by a team member who times
each task and observes without assisting unless the participant is fully stuck:

1. Register or sign in and confirm landing on the correct dashboard for their role.
2. Use the project picker to open a project, including switching company where applicable.
3. Read a project's DORA metrics and identify its current deployment frequency.
4. Find a specific pull request's risk score in the PR list.
5. Link a GitHub account through the "Look up → confirm" flow.
6. *(Admin participants only)* Create a project and invite a team member to it.

- **Session length:** approximately 30–45 minutes per participant, one at a time.
- **Timing:** scheduled for the final two weeks before submission. Results are not yet
  available at the time of writing this report; they will be added once sessions complete.
