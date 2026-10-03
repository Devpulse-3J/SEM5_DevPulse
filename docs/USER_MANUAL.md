# OdinEye — User Manual

**Written for:** people setting up and using OdinEye for the first time (admins and team members).
**Product:** OdinEye — a developer-productivity and delivery-analytics platform. It connects to GitHub, Jira, and Slack, tracks DORA metrics, scores pull requests for risk, and alerts your team when something needs attention.
**Access it at:** `https://odineye.cse23.org`

---

## Table of contents

1. [Getting started](#1-getting-started)
2. [Create your company (admin registration)](#2-create-your-company-admin-registration)
3. [Logging in](#3-logging-in)
4. [The admin dashboard at a glance](#4-the-admin-dashboard-at-a-glance)
5. [Create a project](#5-create-a-project)
6. [Invite members and accept by email](#6-invite-members-and-accept-by-email)
7. [Roles and permissions](#7-roles-and-permissions)
8. [Set up integrations (GitHub, Jira, Slack)](#8-set-up-integrations-github-jira-slack)
9. [Connect an integration to a project](#9-connect-an-integration-to-a-project)
10. [What happens next (the data you get)](#10-what-happens-next-the-data-you-get)
11. [Alert rules](#11-alert-rules)
12. [Settings](#12-settings)
13. [Troubleshooting & FAQ](#13-troubleshooting--faq)
14. [Glossary](#14-glossary)

---

## 1. Getting started

**What you need before you begin:**

- A web browser and the URL: `https://odineye.cse23.org`
- An email address you can check (the first account becomes the company admin).
- Admin access to the GitHub organisation / repositories you want to track.
- An Atlassian (Jira) account, if you use Jira.
- Permission to add an app to your Slack workspace, if you use Slack.

**The big picture — set-up happens in this order:**

1. Register your **company** → you become the **admin**.
2. Create a **project** (one project = one product or codebase).
3. **Invite** your team; they accept through an email link.
4. Connect **GitHub, Jira, and Slack** at the company level.
5. **Connect a repository and Jira board to each project.**
6. OdinEye starts collecting data, computing metrics, and sending alerts.

---

## 2. Create your company (admin registration)

The first person to sign up creates the company and automatically becomes its **admin**.

1. Go to `https://odineye.cse23.org` and open **Register**.
2. At the top, choose **Register as a Company** (the other option, *Individual Account*, is for personal use and does not create a company workspace).
3. Fill in:
   - **Full name** — your name.
   - **Email** — your work email; this becomes the admin login.
   - **Password** — choose a strong password.
   - **Company name** — your organisation's name (this creates the company workspace).
4. Click **Register**.

You now have a company workspace, and your account is its admin. Everything else in this manual is done from that admin account.

> **Note:** There is only ever one company admin role at the company level. Other people you invite become regular members, and you give them **Manager** or **Developer** roles on each project (see [section 7](#7-roles-and-permissions)).

---

## 3. Logging in

- **Admins:** use the **Admin login** page and sign in with your admin email and password.
- **Team members:** use the normal **Login** page with the email and password they set when they accepted their invitation.

If you forget which you are: the admin login takes you to the **admin dashboard** (projects, members, integrations, settings); a member login takes you to the **developer/manager dashboard** (their work, metrics, alerts).

---

## 4. The admin dashboard at a glance

After logging in as admin you land on the **Overview**. The left sidebar has five areas:

| Area | What it's for |
|---|---|
| **Overview** | Your control panel: shortcuts and status flags (e.g. a project with no GitHub repo yet). |
| **Projects** | Create, open, and delete projects. |
| **Members** | See everyone in the company, manage roles, and approve join requests. |
| **Integrations** | Connect GitHub, Jira, and Slack. |
| **Settings** | Company configuration and sensitive controls. |

---

## 5. Create a project

A **project** represents one product or codebase. Metrics, repositories, members, and alerts are all organised under a project.

1. In the sidebar, click **Projects**.
2. Click **+ Create Project**.
3. Enter:
   - **Project name** — e.g. "Payments Service".
   - **Jira project key** *(optional)* — e.g. "PAY". Add this if the team uses Jira, so OdinEye knows which Jira board belongs to this project.
4. Click **Create**.

The project appears in your list with columns for its **GitHub** link, **Members**, and **Created** date.

> **Deleting a project:** on the Projects list, use the **Delete** action on a row. You'll be asked to confirm, because deletion is permanent.

---

## 6. Invite members and accept by email

### 6a. Send an invitation

You can invite someone to a specific project:

1. Open a project from **Projects**.
2. In the **Members** section, click **Invite Member**.
3. Enter the person's **email address**.
4. Choose their **role** for this project — **Developer** or **Manager** (see [section 7](#7-roles-and-permissions)).
5. Click **Send Invite**.

OdinEye emails the person an invitation link.

### 6b. The member accepts by email

1. The invited person opens the email and clicks the invitation link.
2. The link opens the **Register** page with their email already filled in and locked (so they join *your* company, not a new one).
3. They set their **full name** and **password** and submit.
4. They're now a member of your company, with the role you chose on that project.

### 6c. Approving join requests

If someone requests to join your company workspace on their own, their request appears under **Members → pending join requests**. As admin you **Approve** or **Reject** each one. Nobody enters the company without your approval.

---

## 7. Roles and permissions

OdinEye has three roles. The important idea: **Manager and Developer are set per project**, so the same person can be a Manager on one project and a Developer on another.

| Role | Scope | Can do |
|---|---|---|
| **Admin** | Whole company | Everything: projects, members, integrations, settings. One per company. |
| **Manager** | Per project | See the project's full metrics and insights, receive its alerts, manage its team's work. |
| **Developer** | Per project | See their own work, the project's dashboards, and their assigned tasks. |

To change someone's role on a project: open the project, find them in the **Members** section, and use the **role** selector. You can also **remove** a member from a project there.

---

## 8. Set up integrations (GitHub, Jira, Slack)

Integrations are connected **once at the company level** by the admin. Go to **Integrations** in the sidebar — you'll see three cards.

### 8a. GitHub

1. Open the **GitHub** integration.
2. Click to **authorize the GitHub App** (one click — you do **not** paste any personal access token).
3. In GitHub, choose the organisation and select the repositories OdinEye may access, then approve.
4. You're returned to OdinEye, which confirms the installation.

This lets OdinEye read your repositories, receive webhooks for pull requests and deployments, and sync historical data.

### 8b. Jira

1. Open the **Jira** integration.
2. Click **Connect** and sign in to Atlassian (OAuth) to authorise OdinEye.
3. Approve access to your Jira site.
4. Back in OdinEye, your Jira workspace shows as connected. OdinEye can now pull in issues and track workload.

### 8c. Slack

1. Open the **Slack** integration.
2. Click **Connect** and authorise the app for your Slack workspace.
3. Once connected, use **Send test event** (if shown) to confirm a message lands in your Slack channel.

Slack is where OdinEye delivers alerts (high-risk PRs, stale PRs, failed deployments).

---

## 9. Connect an integration to a project

Connecting GitHub at the company level is step one. Step two is linking a specific **repository** (and Jira board) to a specific **project**.

### 9a. Link a GitHub repository to a project

1. Open the project from **Projects**.
2. Go to the **GitHub connection** section.
3. Choose the repository to link from the list of repositories OdinEye can access through the GitHub App.
4. Confirm the link. OdinEye then **syncs historical data** and begins tracking new pull requests and deployments for that project automatically.

> A repository can belong to only one project in one company. If it's already linked elsewhere, OdinEye will tell you.

### 9b. Link Jira to a project

Set the project's **Jira project key** (e.g. "PAY") when you create the project, or on the project's detail page. OdinEye uses it to match Jira issues to the right project.

---

## 10. What happens next (the data you get)

Once a project has a connected repository, OdinEye works on its own:

- **DORA metrics** — deployment frequency, lead time for changes, change failure rate, and time to restore.
- **Pull request risk** — a machine-learning model scores each new pull request; risky ones are surfaced before they become bottlenecks.
- **Team & workload** — who's working on what, combining GitHub activity and Jira issues.
- **Alerts** — delivered to Slack and by email (see next section).

Managers and developers see this through their own dashboards after they log in.

---

## 11. Alert rules

Alert rules decide when OdinEye warns your team. A **Manager** or **Admin** creates them for a project.

Common rule types:

- **Stale PR** — a pull request open longer than a threshold (e.g. 24 hours) is reported.
- **High-risk PR** — a pull request the model scores as high risk is reported.
- **Failed deployment** — the project's managers are emailed automatically when a deployment fails (no rule needed).

When a rule triggers, OdinEye records the alert, posts to the rule's **Slack channel**, and emails the project's **managers**. Each real problem is alerted on once.

---

## 12. Settings

The **Settings** area holds company-level configuration. It also contains the most sensitive controls — for example **Delete Organisation** — which are kept behind a confirmation. Use these with care; deleting the organisation cannot be undone.

---

## 13. Troubleshooting & FAQ

**I registered but didn't get a company workspace.**
You probably chose *Individual Account*. Register again choosing **Register as a Company**.

**An invited member says the email link doesn't pre-fill their address.**
Make sure they open the link from the invitation email directly, rather than navigating to the register page manually. The invite link carries their email and the join token.

**The GitHub repository dropdown is empty when linking it to a project.**
The GitHub App either isn't installed yet, or wasn't granted access to that repository. Re-open the **GitHub** integration, authorise the App, and make sure the repository is selected during installation.

**"This repository is already connected to another company/project."**
A repository can be linked to only one project. Remove it from the other project first, or pick a different repository.

**No DORA metrics are showing for a project.**
Check that the project has a **linked repository** (section 9) and that there has been recent activity (pull requests, deployments). Historical sync runs when you first link the repo.

**Jira tasks aren't appearing for a developer.**
A Jira task only shows for a developer if their Jira account email matches their OdinEye email, and the project has a **Jira project key** set.

**Alerts aren't arriving in Slack.**
Confirm Slack is connected (section 8c) and that the alert rule has a valid **Slack channel**. Use the **Send test event** button to verify delivery.

---

## 14. Glossary

- **Company** — your organisation's workspace. Created by the first (admin) registration.
- **Project** — one product or codebase; the unit metrics and members are organised under.
- **Admin** — company-level owner; sets up everything. One per company.
- **Manager / Developer** — per-project roles granted to members.
- **Integration** — a connection to an external tool (GitHub, Jira, or Slack).
- **DORA metrics** — four industry-standard measures of software delivery performance.
- **PR risk score** — a machine-learning estimate of how risky a pull request is.
- **Alert rule** — a condition that, when met, notifies the team via Slack and email.
