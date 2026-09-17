"""Build the model's feature row for a PR, sourced from Postgres.

This is the production twin of `training/build_snapshots.py::features_at_t0`.
The training script and this module MUST agree column-for-column and
value-for-value; a mismatch here is the classic cause of a model that scores
well offline and quietly misbehaves in production.

Where the two differ in mechanism (JSONL replay vs. SQL) the training
behaviour is authoritative and is reproduced deliberately:

  * T0 is `pull_requests.created_at`, normalised to UTC. Postgres returns
    timestamptz in the *session* timezone; if that is not UTC, `created_hour`
    silently shifts and every hour-of-day split in the model is wrong. Hence
    the explicit `_as_utc`.

  * A prior PR contributes to history only once it has RESOLVED, and only if
    it resolved strictly before T0. Filtering on `created_at < t0` instead —
    the intuitive reading — leaks outcomes that were still unknown at T0 and
    inflates `author_prior_prs`.

  * "No history" is the sentinel -1.0, NOT 0.0. Training used -1.0 so the
    model could tell "never had a prior PR" apart from "had priors, none went
    stale". Substituting 0.0 tells the model a brand-new author has a perfect
    track record.

  * Repo history is the rolling window of the last REPO_WINDOW *resolutions*
    before T0, matching the bisect window in HistoryTracker.repo_stats.

Everything is scoped to company_id: this is a multi-tenant database, and an
unscoped history query would compute one company's features from another's
pull requests.
"""
from __future__ import annotations

import statistics
from dataclasses import dataclass
from datetime import datetime, timezone

from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from app.database.models import PullRequest

# Rolling window for repo-level history, in PRs (not days) — REPO_WINDOW in
# training/build_snapshots.py.
REPO_WINDOW = 200

# Fallback when the model bundle does not declare one. Training used 14.
DEFAULT_HORIZON_DAYS = 14

# The sentinel training used for "this entity has no prior history at all".
NO_HISTORY = -1.0

# Column order is asserted against the bundle's feature_columns at predict time.
FEATURE_COLUMNS = [
    "title_length",
    "body_length",
    "body_is_empty",
    "base_branch",
    "author_association",
    "created_hour",
    "created_weekday",
    "created_is_weekend",
    "author_prior_prs",
    "author_prior_stale_rate",
    "author_prior_median_days",
    "author_is_new",
    "repo_recent_stale_rate",
    "repo_recent_median_days",
]


class PullRequestNotFound(LookupError):
    """Raised when the requested pr_id is absent, or belongs to another company."""


@dataclass(frozen=True)
class _Resolution:
    """A prior PR that had already finished before T0."""

    resolved_at: datetime
    resolution_days: float


def _as_utc(value: datetime) -> datetime:
    """Normalise to UTC. Naive values are assumed UTC, as timestamptz implies."""
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _resolution_days(created_at: datetime, resolved_at: datetime) -> float:
    return (_as_utc(resolved_at) - _as_utc(created_at)).total_seconds() / 86400.0


# `merged_at` wins over `closed_at`, mirroring `merged_at or closed_at` in
# training. A merged PR always has closed_at set too, so the order matters.
_RESOLVED_AT = func.coalesce(PullRequest.merged_at, PullRequest.closed_at)


def _resolved_before(t0: datetime):
    """Prior PRs whose outcome was already observable standing at T0."""
    return or_(
        PullRequest.merged_at.isnot(None),
        PullRequest.closed_at.isnot(None),
    ), _RESOLVED_AT < t0


def load_pull_request(
    db: Session, pr_id: int, company_id: int | None = None
) -> PullRequest:
    stmt = select(PullRequest).where(PullRequest.pr_id == pr_id)
    if company_id is not None:
        stmt = stmt.where(PullRequest.company_id == company_id)
    pr = db.execute(stmt).scalar_one_or_none()
    if pr is None:
        raise PullRequestNotFound(f"pull request {pr_id} not found")
    return pr


def _fetch_resolutions(
    db: Session,
    *,
    company_id: int,
    t0: datetime,
    author_id: int | None = None,
    repo_id: int | None = None,
    limit: int | None = None,
    exclude_pr_id: int | None = None,
) -> list[_Resolution]:
    """Resolutions strictly before T0, newest first when `limit` is set."""
    has_outcome, before_t0 = _resolved_before(t0)
    stmt = (
        select(PullRequest.created_at, _RESOLVED_AT.label("resolved_at"))
        .where(PullRequest.company_id == company_id, has_outcome, before_t0)
        .order_by(_RESOLVED_AT.desc())
    )
    if author_id is not None:
        stmt = stmt.where(PullRequest.author_id == author_id)
    if repo_id is not None:
        stmt = stmt.where(PullRequest.repo_id == repo_id)
    if exclude_pr_id is not None:
        stmt = stmt.where(PullRequest.pr_id != exclude_pr_id)
    if limit is not None:
        stmt = stmt.limit(limit)

    return [
        _Resolution(resolved_at=resolved_at,
                    resolution_days=_resolution_days(created_at, resolved_at))
        for created_at, resolved_at in db.execute(stmt).all()
    ]


def _stale_stats(
    resolutions: list[_Resolution], horizon_days: int
) -> tuple[float, float]:
    """(stale_rate, median_days) over already-finished PRs."""
    days = [r.resolution_days for r in resolutions]
    stale = sum(1 for d in days if d > horizon_days)
    return stale / len(days), statistics.median(days)


def author_stats(
    db: Session,
    *,
    company_id: int,
    author_id: int | None,
    t0: datetime,
    horizon_days: int,
    exclude_pr_id: int | None = None,
) -> dict:
    """HistoryTracker.author_stats, over PRs the author had already finished.

    An unresolvable author (author_id NULL, e.g. a GitHub user never matched to
    a DevPulse account) is treated as having no history — the same branch a
    genuinely first-time author takes.
    """
    resolutions: list[_Resolution] = []
    if author_id is not None:
        resolutions = _fetch_resolutions(
            db,
            company_id=company_id,
            t0=t0,
            author_id=author_id,
            exclude_pr_id=exclude_pr_id,
        )

    if not resolutions:
        return {
            "author_prior_prs": 0,
            "author_prior_stale_rate": NO_HISTORY,
            "author_prior_median_days": NO_HISTORY,
            "author_is_new": 1,
        }

    stale_rate, median_days = _stale_stats(resolutions, horizon_days)
    return {
        "author_prior_prs": len(resolutions),
        "author_prior_stale_rate": stale_rate,
        "author_prior_median_days": median_days,
        "author_is_new": 0,
    }


def repo_stats(
    db: Session,
    *,
    company_id: int,
    repo_id: int,
    t0: datetime,
    horizon_days: int,
    exclude_pr_id: int | None = None,
) -> dict:
    """HistoryTracker.repo_stats — the last REPO_WINDOW resolutions before T0.

    Training collected a single repository, so its "global" rolling window was
    in fact one repo's window. Scoping by repo_id here is the faithful
    equivalent, not a change of definition.
    """
    resolutions = _fetch_resolutions(
        db,
        company_id=company_id,
        t0=t0,
        repo_id=repo_id,
        limit=REPO_WINDOW,
        exclude_pr_id=exclude_pr_id,
    )
    if not resolutions:
        return {
            "repo_recent_stale_rate": NO_HISTORY,
            "repo_recent_median_days": NO_HISTORY,
        }

    stale_rate, median_days = _stale_stats(resolutions, horizon_days)
    return {
        "repo_recent_stale_rate": stale_rate,
        "repo_recent_median_days": median_days,
    }


def features_from_pr(
    db: Session, pr: PullRequest, horizon_days: int = DEFAULT_HORIZON_DAYS
) -> dict:
    """The single definition of a feature row, built from a loaded PR.

    Mirrors training/build_snapshots.py::features_at_t0 exactly.
    """
    t0 = _as_utc(pr.created_at)
    body = pr.description or ""

    row: dict = {
        # Immutable properties of the PR as opened.
        "title_length": len(pr.title or ""),
        "body_length": len(body),
        "body_is_empty": int(not body.strip()),
        "base_branch": pr.base_branch,
        # Left raw, including None. The bundle's OneHotEncoder was fitted with
        # handle_unknown="ignore", so an unseen or missing value encodes as an
        # all-zero block rather than raising.
        "author_association": pr.author_association,
        "created_hour": t0.hour,
        "created_weekday": t0.weekday(),
        "created_is_weekend": int(t0.weekday() >= 5),
    }

    # A PR must never contribute to its own history, which matters when
    # rescoring a PR that has since resolved.
    row.update(
        author_stats(
            db,
            company_id=pr.company_id,
            author_id=pr.author_id,
            t0=t0,
            horizon_days=horizon_days,
            exclude_pr_id=pr.pr_id,
        )
    )
    row.update(
        repo_stats(
            db,
            company_id=pr.company_id,
            repo_id=pr.repo_id,
            t0=t0,
            horizon_days=horizon_days,
            exclude_pr_id=pr.pr_id,
        )
    )
    return row


def build_features_for_pr(
    db: Session,
    pr_id: int,
    company_id: int | None = None,
    horizon_days: int = DEFAULT_HORIZON_DAYS,
) -> dict:
    """Query the database and assemble the model's feature row for one PR.

    Raises PullRequestNotFound if the PR does not exist for this company.
    """
    pr = load_pull_request(db, pr_id, company_id)
    return features_from_pr(db, pr, horizon_days=horizon_days)
