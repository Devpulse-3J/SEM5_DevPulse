# OdinEye (DevPulse) — Final Test Script

**Written for:** the team and the evaluator — how to test OdinEye before the final demo, covering automated tests and manual end-to-end checks.
**System under test:** OdinEye / DevPulse — Spring Boot microservices + FastAPI analytics + Next.js frontend, behind an API gateway, on a shared PostgreSQL database.
**Live URL:** `https://odineye.cse23.org`

**How to read a test case:** each has an **ID**, **Preconditions**, **Steps**, and an **Expected result**. Record **Pass/Fail** and notes in the result column. Do the automated tests first (Part B), then the manual end-to-end tests (Part C).

---

## Part A — Test environment

### A1. What you need
- The repositories checked out (backend + frontend).
- Java 17 and Maven (for backend tests). No `./mvnw` wrapper is committed — run Maven from `backend/`.
- Python 3.11 + virtualenv (for analytics tests).
- Node.js (for frontend tests).
- For manual tests: the live site `https://odineye.cse23.org`, a test email inbox, and access to a GitHub org, a Jira site, and a Slack workspace.

### A2. Test data / accounts
- A throwaway email for the admin (company) registration.
- One or two throwaway emails for invited members.
- A GitHub repository you can open a pull request on (to exercise PR risk + alerts).

> **Important:** manual tests write real data. Use a **test company**, not a production one. Do not run destructive DB operations against the shared database.

---

## Part B — Automated tests

### B1. Backend — Java (per service)
Run from the `backend/` directory. There is no `./mvnw`; use `mvn`.

| ID | Service | Command | Expected |
|---|---|---|---|
| AT-01 | api-gateway | `cd backend && mvn -pl api-gateway -am test` | All tests pass (JWT validation, header stripping, rate-limit keying, error contract). |
| AT-02 | auth-service | `cd backend && mvn -pl auth-service -am test` | All tests pass. |
| AT-03 | integration-service | `cd backend && mvn -pl integration-service -am test` | All tests pass. |
| AT-04 | metrics-service | `cd backend && mvn -pl metrics-service -am test` | All tests pass. |
| AT-05 | notification-service | `cd backend && mvn -pl notification-service -am test` | All tests pass (stale-PR, high-risk, deployment-failure notifiers). |
| AT-06 | everything | `cd backend && mvn test` | The whole reactor builds and all modules' tests pass. |

> **Gotcha:** `mvn -pl <service> test -Dtest=ClassName` can fail in the multi-module reactor because of `shared-contracts`. Run the whole module's suite instead.

**How to read results:** each module prints `Tests run: N, Failures: 0, Errors: 0`. Any non-zero Failures/Errors = FAIL; read `target/surefire-reports/` for the detail.

### B2. Analytics — Python (ML service)
```bash
cd backend/analytics-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
pytest
```

| ID | Area | Expected |
|---|---|---|
| AT-07 | Feature extraction (`test_feature_extractor.py`) | All pass. |
| AT-08 | PR-events consumer (`test_pr_events_consumer.py`) | All pass, including the `__TypeId__` header on the published high-risk alert and the reconnect/backoff behaviour. |

### B3. Frontend — Next.js
```bash
cd <frontend-repo>
npm install
npm run test          # vitest
npm run lint          # eslint
npm run build         # production build must succeed
```

| ID | Check | Expected |
|---|---|---|
| AT-09 | `npm run test` | All vitest suites pass. |
| AT-10 | `npm run lint` | No lint errors. |
| AT-11 | `npm run build` | Build completes with no type or build errors. |

---

## Part C — Manual end-to-end tests

Follow these in order; later tests depend on earlier ones. (They mirror the User Manual.)

### C1. Registration & company

| ID | Preconditions | Steps | Expected result |
|---|---|---|---|
| E2E-01 | Not logged in | Open `/register`, choose **Register as a Company**, fill name/email/password/company name, submit. | Company created; you are its admin; you reach the admin dashboard. |
| E2E-02 | — | Register again as **Individual Account**. | Account created with no company workspace (confirms the two modes differ). |

### C2. Login

| ID | Preconditions | Steps | Expected result |
|---|---|---|---|
| E2E-03 | Admin exists | Log out, open **Admin login**, sign in. | Lands on the admin dashboard (Overview). |
| E2E-04 | Wrong password | Try admin login with a wrong password. | Rejected with a clear error; no access. |

### C3. Projects

| ID | Preconditions | Steps | Expected result |
|---|---|---|---|
| E2E-05 | Admin logged in | **Projects → + Create Project**, enter name + Jira key, Create. | Project appears in the list with GitHub/Members/Created columns. |
| E2E-06 | A project exists | Click **Delete** on a project, confirm. | Confirmation shown first; after confirm the project is removed. |

### C4. Members & invitations

| ID | Preconditions | Steps | Expected result |
|---|---|---|---|
| E2E-07 | A project exists | Open the project → **Invite Member**, enter a test email, pick **Developer**, Send. | Invitation email is sent to that address. |
| E2E-08 | Invitation sent | Open the email, click the link. | Register page opens with the email pre-filled and locked. |
| E2E-09 | On the invite register page | Set full name + password, submit. | The member joins the admin's company with the Developer role on that project. |
| E2E-10 | A pending join request exists | Admin → **Members → pending requests**, Approve one. | The person becomes an approved member; rejecting instead removes the request. |

### C5. Roles & permissions

| ID | Preconditions | Steps | Expected result |
|---|---|---|---|
| E2E-11 | A member on a project | Open the project, change the member's role to **Manager**. | Role updates; the member now gets the manager view/alerts for that project. |
| E2E-12 | Same person on two projects | Make them Manager on project A, Developer on project B. | Each project reflects its own role (per-project roles work). |
| E2E-13 | Logged in as a Developer | View a project dashboard. | Sees developer dashboards; cannot access admin-only pages. |

### C6. Integrations (company level)

| ID | Preconditions | Steps | Expected result |
|---|---|---|---|
| E2E-14 | Admin | **Integrations → GitHub**, authorize the GitHub App, select repos, approve. | Returns to OdinEye; installation shows as connected. |
| E2E-15 | Admin | **Integrations → Jira**, Connect, sign in to Atlassian, approve. | Jira shows as connected. |
| E2E-16 | Admin | **Integrations → Slack**, Connect, authorize; click **Send test event**. | A test message arrives in the Slack channel. |

### C7. Connect integration to a project

| ID | Preconditions | Steps | Expected result |
|---|---|---|---|
| E2E-17 | GitHub connected | Open a project → **GitHub connection**, pick a repo, confirm. | Repo linked; historical sync runs; the project's GitHub column shows the repo. |
| E2E-18 | Repo already linked elsewhere | Try to link the same repo to another project/company. | Blocked with a clear "already connected" message. |
| E2E-19 | Jira connected + project key set | Confirm the project has its Jira key. | Jira issues for that key are associated with the project. |

### C8. Data & metrics

| ID | Preconditions | Steps | Expected result |
|---|---|---|---|
| E2E-20 | Repo linked, has history | Open the project's **DORA Metrics**. | Deployment frequency, lead time, change failure rate, and MTTR display. |
| E2E-21 | Open a new PR on the linked repo | Wait for ingestion, open **PR Risk & Insights**. | The new PR appears with a risk score/category. |
| E2E-22 | Jira connected | Open **Team & Workload** as the assignee. | Assigned Jira tasks show for the matching developer. |

### C9. Alerts

| ID | Preconditions | Steps | Expected result |
|---|---|---|---|
| E2E-23 | Manager/Admin | Create a **STALE_PR** rule (e.g. 24h) with a Slack channel. | Rule saved and listed. |
| E2E-24 | An open PR older than the threshold | Wait for the scheduled check (≤15 min). | One alert posted to the rule's Slack channel and emailed to the project's managers; a PR is alerted only once. |
| E2E-25 | A **HIGH_RISK_PR** rule exists; open a PR that scores high | Wait for scoring. | High-risk alert posted/emailed once for that PR. |
| E2E-26 | A linked project with a manager | Trigger a failed deployment on the repo. | The project's managers receive the "Deployment failed" email; duplicates are suppressed. |

---

## Part D — Security tests

| ID | Steps | Expected result |
|---|---|---|
| SEC-01 | Call a protected API through the gateway with **no** token (e.g. `GET /api/metrics/dora`). | `401` with the JSON error shape `{status, error, path}`. |
| SEC-02 | Call it with a malformed/expired token. | `401`. |
| SEC-03 | Send a request with a forged `X-User-Id` header plus a valid token. | The gateway strips the forged header and sets it from the token (covered by `JwtAuthenticationFilterHeaderTest`). |
| SEC-04 | Hit `/api/auth/login` rapidly many times. | Requests are rate-limited after the burst (per-IP). |
| SEC-05 | Confirm backend service ports (8081–8084, 8000) are not reachable from the public internet, only the gateway (8080). | Direct access refused / filtered; all traffic goes through the gateway. |

> SEC-01 to SEC-04 are also covered by the automated gateway suite (AT-01). SEC-05 is an infrastructure/security-group check, done manually.

---

## Part E — Non-functional checks

| ID | Check | Expected |
|---|---|---|
| NFR-01 | Reliability: send the same GitHub webhook twice. | Only one deployment/alert recorded (idempotent). |
| NFR-02 | Loose coupling: a PR event is consumed by metrics and analytics independently. | Both act on one event; no service calls another directly. |
| NFR-03 | Observability: each service's `/actuator/health` returns `UP`. | Healthy. |
| NFR-04 | Deployability: a merge to `main` triggers CI/CD and redeploys. | New version live after the pipeline. |
