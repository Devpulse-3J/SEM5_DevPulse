# Metrics Service API Guide

The browser-facing base URL is `http://localhost:8080/api/metrics`. The gateway strips `/api`, so
metrics-service controllers are internally mapped under `/metrics` on port 8083.

All IDs in JSON are strings. Times are UTC ISO-8601 values. JSON fields with no valid sample or
source value may be `null`.

## Authentication

Send the access token issued by auth-service:

```http
Authorization: Bearer <access-token>
```

The gateway converts it to trusted identity headers. Do not send identity headers from a normal
client. For direct local testing on port 8083 only, supply `X-User-Id` and `X-Company-Id` yourself.

## GET `/api/metrics/dora`

Returns live current and previous DORA windows plus stored daily history.

| Query parameter | Required | Default | Range |
|---|---|---:|---:|
| `projectId` | yes | — | positive integer |
| `windowDays` | no | 30 | 1–365 |
| `historyDays` | no | 30 | 1–365 |

```bash
curl "http://localhost:8080/api/metrics/dora?projectId=1&windowDays=30&historyDays=30" \
  -H "Authorization: Bearer $TOKEN"
```

Example response:

```json
{
  "projectId": "1",
  "projectName": "payments-api",
  "repoCount": 2,
  "calculatedAt": "2026-08-21T06:30:00Z",
  "windowDays": 30,
  "metrics": [
    {
      "key": "deploymentFrequency",
      "value": 0.2667,
      "unit": "deployments/day",
      "rating": "HIGH",
      "previousValue": 0.1667,
      "sampleSize": 8,
      "history": [{ "date": "2026-08-21", "value": 0.2667 }]
    },
    {
      "key": "changeFailureRate",
      "value": 12.50,
      "unit": "%",
      "rating": "MEDIUM",
      "previousValue": 10.00,
      "sampleSize": 8,
      "history": [{ "date": "2026-08-21", "value": 12.50 }]
    }
  ]
}
```

There are four metric objects: `deploymentFrequency`, `leadTime`, `mttr`, and
`changeFailureRate`. A metric with no valid denominator has `value: null`,
`previousValue: null` when applicable, and `rating: "NOT_AVAILABLE"`.

## GET `/api/metrics/prs`

Returns project PRs newest first. Reviews and checks are embedded; `riskAnalysis` is always `null`
in this list because analytics-service owns risk predictions.

| Query parameter | Required | Default | Range |
|---|---|---:|---:|
| `projectId` | yes | — | positive integer |
| `limit` | no | 100 | 1–500 |
| `offset` | no | 0 | 0 or greater |

```bash
curl "http://localhost:8080/api/metrics/prs?projectId=1&limit=50&offset=0" \
  -H "Authorization: Bearer $TOKEN"
```

```json
[
  {
    "id": "42",
    "number": 118,
    "title": "Add payment retry policy",
    "description": "Retries transient provider failures",
    "author": "Demo Dev",
    "authorAvatar": "https://example.test/avatar.png",
    "repositoryId": "3",
    "repositoryName": "payments-api",
    "status": "open",
    "headBranch": "feature/retry-policy",
    "baseBranch": "main",
    "additions": 91,
    "deletions": 12,
    "changedFiles": 6,
    "url": "https://github.com/example/payments-api/pull/118",
    "createdAt": "2026-08-20T08:00:00Z",
    "updatedAt": "2026-08-20T09:00:00Z",
    "mergedAt": null,
    "reviews": [
      {
        "id": "8",
        "reviewerName": "Demo Admin",
        "reviewerAvatar": null,
        "state": "approved",
        "submittedAt": "2026-08-20T10:00:00Z"
      }
    ],
    "checks": [
      { "id": "5", "name": "build", "status": "SUCCESS", "url": null }
    ],
    "riskAnalysis": null
  }
]
```

PR `status` is `draft`, `open`, `merged`, or `closed`. Draft takes precedence over the stored
state.

## GET `/api/metrics/deployments`

Returns deployment history newest first.

| Query parameter | Required | Default | Allowed values |
|---|---|---:|---|
| `projectId` | yes | — | positive integer |
| `environment` | no | all | `development`, `staging`, `production` |
| `status` | no | all | `pending`, `success`, `failed`, `rolled_back` |
| `limit` | no | 100 | 1–500 |
| `offset` | no | 0 | 0 or greater |

```bash
curl "http://localhost:8080/api/metrics/deployments?projectId=1&environment=production&status=success" \
  -H "Authorization: Bearer $TOKEN"
```

```json
[
  {
    "id": "31",
    "externalId": "9000123",
    "commitSha": "0123456789012345678901234567890123456789",
    "environment": "production",
    "status": "success",
    "deployedAt": "2026-08-20T14:00:00Z",
    "failureRecoveredAt": null,
    "triggeredByUserId": "2",
    "triggeredByName": "Demo Dev",
    "leadTimeHours": 18.50
  }
]
```

`leadTimeHours` is `null` when the deployment has no linked commit or timestamps are invalid.

## GET `/api/metrics/workload`

Returns workload for every current project member.

| Query parameter | Required | Default | Range |
|---|---|---:|---:|
| `projectId` | yes | — | positive integer |
| `windowDays` | no | 30 | 1–365 |

```bash
curl "http://localhost:8080/api/metrics/workload?projectId=1&windowDays=30" \
  -H "Authorization: Bearer $TOKEN"
```

```json
[
  {
    "userId": "2",
    "name": "Demo Dev",
    "activePrs": 3,
    "loadPct": 75.00,
    "cycleTimeHours": 31.25
  }
]
```

`loadPct` uses the configured target active PR count (default 4) and may exceed 100.
`cycleTimeHours` is `null` when the member has no PR merged in the requested window.

## Error responses

All errors have the same envelope:

```json
{
  "error": {
    "code": "PROJECT_ACCESS_DENIED",
    "message": "The authenticated user is not a member of this project"
  }
}
```

| HTTP status | Typical codes |
|---:|---|
| 400 | `INVALID_REQUEST`, `INVALID_FILTER` |
| 401 | `AUTHENTICATION_REQUIRED`, `INVALID_IDENTITY_CONTEXT`, `USER_CONTEXT_NOT_FOUND` |
| 403 | `PROJECT_ACCESS_DENIED` |
| 404 | `PROJECT_NOT_FOUND` |
| 500 | `INTERNAL_ERROR` |

## Direct local-service example

Use this only when bypassing the gateway during development:

```bash
curl "http://localhost:8083/metrics/dora?projectId=1" \
  -H "X-User-Id: 1" \
  -H "X-Company-Id: 1"
```

The Actuator health endpoint remains available at `GET /actuator/health`.

