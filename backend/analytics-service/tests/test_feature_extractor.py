"""Parity tests: the SQL feature extractor vs. the training feature builder.

The point of these tests is narrow and important. `training/build_snapshots.py`
defines the features the model was fitted on, reading a JSONL cache.
`app/ml/feature_extractor.py` rebuilds the same row from Postgres. If the two
ever disagree the model keeps returning confident, wrong numbers — there is no
error to notice.

So the central test replays real PRs from the training cache through BOTH
paths and asserts the rows are identical, field for field.
"""
from __future__ import annotations

import json
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT))
sys.path.insert(0, str(SERVICE_ROOT / "training"))

from app.database.models import Base, PullRequest  # noqa: E402
from app.ml import feature_extractor  # noqa: E402

build_snapshots = pytest.importorskip(
    "build_snapshots", reason="training/build_snapshots.py is required for parity"
)

CACHE = SERVICE_ROOT / "training" / "pr_cache.jsonl"
HORIZON = 14
COMPANY_ID = 1
REPO_ID = 1


@pytest.fixture
def db() -> Session:
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    with Session(engine) as session:
        yield session


def _utc(*args) -> datetime:
    return datetime(*args, tzinfo=timezone.utc)


def _make_pr(pr_id: int, **overrides) -> PullRequest:
    defaults = dict(
        pr_id=pr_id,
        company_id=COMPANY_ID,
        repo_id=REPO_ID,
        github_pr_number=pr_id,
        title="a title",
        description="a body",
        author_id=1,
        base_branch="main",
        is_draft=False,
        state="open",
        author_association="CONTRIBUTOR",
        created_at=_utc(2026, 1, 15, 9, 0),
        merged_at=None,
        closed_at=None,
    )
    defaults.update(overrides)
    return PullRequest(**defaults)


# --------------------------------------------------------------- direct fields


def test_direct_fields_match_the_open_time_facts(db: Session):
    db.add(_make_pr(1, title="hello", description="body text",
                    base_branch="release", author_association="MEMBER",
                    created_at=_utc(2026, 1, 17, 14, 30)))  # Saturday
    db.commit()

    f = feature_extractor.build_features_for_pr(db, 1, COMPANY_ID, HORIZON)

    assert f["title_length"] == 5
    assert f["body_length"] == 9
    assert f["body_is_empty"] == 0
    assert f["base_branch"] == "release"
    assert f["author_association"] == "MEMBER"
    assert f["created_hour"] == 14
    assert f["created_weekday"] == 5
    assert f["created_is_weekend"] == 1


@pytest.mark.parametrize("body", [None, "", "   ", "\n\t "])
def test_whitespace_only_body_counts_as_empty(db: Session, body):
    db.add(_make_pr(1, description=body))
    db.commit()
    f = feature_extractor.build_features_for_pr(db, 1, COMPANY_ID, HORIZON)
    assert f["body_is_empty"] == 1


def test_created_hour_is_utc_regardless_of_session_timezone(db: Session):
    """A non-UTC timestamp must be converted, not read off naively.

    Postgres returns timestamptz in the connection's timezone, so an offset
    other than UTC is reachable in production. The PR is left transient here
    because SQLite has no timestamptz and would silently drop the offset,
    testing the storage layer rather than the conversion.
    """
    tz = timezone(timedelta(hours=5, minutes=30))
    pr = _make_pr(1, created_at=datetime(2026, 1, 15, 2, 0, tzinfo=tz))

    f = feature_extractor.features_from_pr(db, pr, HORIZON)

    # 2026-01-15 02:00+05:30 is 2026-01-14 20:30 UTC — a different day, and a
    # different weekday, from the value as written.
    assert f["created_hour"] == 20
    assert f["created_weekday"] == 2  # Wednesday the 14th, not Thursday the 15th
    assert f["created_is_weekend"] == 0


def test_naive_timestamps_are_assumed_utc():
    naive = datetime(2026, 1, 15, 7, 0)
    assert feature_extractor._as_utc(naive) == _utc(2026, 1, 15, 7, 0)


# ------------------------------------------------------------------- sentinels


def test_no_history_uses_the_training_sentinel_not_zero(db: Session):
    """-1.0 means "no priors". 0.0 would mean "priors, none stale"."""
    db.add(_make_pr(1))
    db.commit()

    f = feature_extractor.build_features_for_pr(db, 1, COMPANY_ID, HORIZON)

    assert f["author_prior_prs"] == 0
    assert f["author_is_new"] == 1
    assert f["author_prior_stale_rate"] == -1.0
    assert f["author_prior_median_days"] == -1.0
    assert f["repo_recent_stale_rate"] == -1.0
    assert f["repo_recent_median_days"] == -1.0


def test_null_author_is_treated_as_no_history(db: Session):
    db.add(_make_pr(1, author_id=None))
    db.commit()
    f = feature_extractor.build_features_for_pr(db, 1, COMPANY_ID, HORIZON)
    assert f["author_is_new"] == 1
    assert f["author_prior_stale_rate"] == -1.0


# --------------------------------------------------------------------- history


def test_only_priors_resolved_before_t0_count(db: Session):
    """The subtle one: created-before is not the same as resolved-before."""
    t0 = _utc(2026, 3, 1, 12, 0)
    db.add_all([
        # Resolved before T0 — counts. Took 2 days.
        _make_pr(1, created_at=_utc(2026, 1, 1), merged_at=_utc(2026, 1, 3)),
        # Created before T0 but resolved AFTER it — outcome unknown at T0.
        _make_pr(2, created_at=_utc(2026, 2, 1), merged_at=_utc(2026, 4, 1)),
        # Still open — never counts.
        _make_pr(3, created_at=_utc(2026, 2, 10)),
        _make_pr(9, created_at=t0),
    ])
    db.commit()

    f = feature_extractor.build_features_for_pr(db, 9, COMPANY_ID, HORIZON)

    assert f["author_prior_prs"] == 1
    assert f["author_is_new"] == 0
    assert f["author_prior_median_days"] == pytest.approx(2.0)
    assert f["author_prior_stale_rate"] == 0.0


def test_stale_rate_uses_the_horizon(db: Session):
    t0 = _utc(2026, 6, 1)
    db.add_all([
        _make_pr(1, created_at=_utc(2026, 1, 1), merged_at=_utc(2026, 1, 2)),   # 1d
        _make_pr(2, created_at=_utc(2026, 2, 1), closed_at=_utc(2026, 3, 1)),   # 28d
        _make_pr(3, created_at=t0),
    ])
    db.commit()

    f = feature_extractor.build_features_for_pr(db, 3, COMPANY_ID, HORIZON)

    assert f["author_prior_prs"] == 2
    assert f["author_prior_stale_rate"] == pytest.approx(0.5)
    assert f["author_prior_median_days"] == pytest.approx(14.5)


def test_merged_at_wins_over_closed_at(db: Session):
    """A merged PR has both timestamps; training used `merged_at or closed_at`."""
    db.add_all([
        _make_pr(1, created_at=_utc(2026, 1, 1),
                 merged_at=_utc(2026, 1, 3), closed_at=_utc(2026, 1, 9)),
        _make_pr(2, created_at=_utc(2026, 5, 1)),
    ])
    db.commit()
    f = feature_extractor.build_features_for_pr(db, 2, COMPANY_ID, HORIZON)
    assert f["author_prior_median_days"] == pytest.approx(2.0)


def test_history_is_scoped_by_author_repo_and_company(db: Session):
    t0 = _utc(2026, 6, 1)
    resolved = dict(created_at=_utc(2026, 1, 1), merged_at=_utc(2026, 1, 21))  # stale
    db.add_all([
        _make_pr(1, author_id=2, **resolved),                      # other author
        _make_pr(2, repo_id=99, author_id=2, **resolved),          # other repo
        _make_pr(3, company_id=2, author_id=1, **resolved),        # other tenant
        _make_pr(4, created_at=t0),
    ])
    db.commit()

    f = feature_extractor.build_features_for_pr(db, 4, COMPANY_ID, HORIZON)

    # Author 1 has no priors of their own in company 1.
    assert f["author_prior_prs"] == 0
    # The repo window sees only PR 1 — not the other repo, not the other tenant.
    assert f["repo_recent_stale_rate"] == pytest.approx(1.0)
    assert f["repo_recent_median_days"] == pytest.approx(20.0)


def test_a_pr_never_contributes_to_its_own_history(db: Session):
    """Rescoring a since-resolved PR must not feed its outcome back in."""
    db.add(_make_pr(1, created_at=_utc(2026, 1, 1), merged_at=_utc(2026, 2, 1)))
    db.commit()
    f = feature_extractor.build_features_for_pr(db, 1, COMPANY_ID, HORIZON)
    assert f["author_prior_prs"] == 0
    assert f["repo_recent_stale_rate"] == -1.0


def test_repo_window_is_capped(db: Session):
    """Only the last REPO_WINDOW resolutions before T0 are in scope."""
    base = _utc(2026, 1, 1)
    prs = []
    # 250 fast PRs, then 10 slow ones resolving most recently.
    for i in range(250):
        created = base + timedelta(hours=i)
        prs.append(_make_pr(i + 1, author_id=2, created_at=created,
                            merged_at=created + timedelta(days=1)))
    for i in range(10):
        created = base + timedelta(days=200, hours=i)
        prs.append(_make_pr(1000 + i, author_id=2, created_at=created,
                            merged_at=created + timedelta(days=30)))
    prs.append(_make_pr(9999, created_at=base + timedelta(days=400)))
    db.add_all(prs)
    db.commit()

    f = feature_extractor.build_features_for_pr(db, 9999, COMPANY_ID, HORIZON)

    assert f["repo_recent_stale_rate"] == pytest.approx(10 / feature_extractor.REPO_WINDOW)


# ---------------------------------------------------- parity with the trainer


def _load_cache(limit: int) -> list[dict]:
    if not CACHE.exists():
        pytest.skip(f"{CACHE} not present")
    prs = []
    with CACHE.open(encoding="utf-8") as fh:
        for line in fh:
            if line.strip():
                prs.append(json.loads(line))
    prs.sort(key=lambda p: p["created_at"])
    return prs[:limit]


def test_matches_build_snapshots_on_real_cache_data(db: Session):
    """Replay real PRs through both implementations and diff the rows.

    This is the test that would catch a drift between training and serving.
    """
    prs = _load_cache(400)
    logins = sorted({(p.get("user") or {}).get("login") or "<unknown>" for p in prs})
    author_ids = {login: i + 1 for i, login in enumerate(logins)}

    # --- load the same PRs into the database ---
    rows = []
    for n, pr in enumerate(prs, start=1):
        login = (pr.get("user") or {}).get("login") or "<unknown>"
        rows.append(_make_pr(
            n,
            github_pr_number=pr.get("number") or n,
            title=pr.get("title") or "",
            description=pr.get("body"),
            author_id=author_ids[login],
            base_branch=(pr.get("base") or {}).get("ref") or "main",
            author_association=pr.get("author_association"),
            created_at=build_snapshots._parse_ts(pr["created_at"]),
            merged_at=build_snapshots._parse_ts(pr.get("merged_at")),
            closed_at=build_snapshots._parse_ts(pr.get("closed_at")),
        ))
    db.add_all(rows)
    db.commit()

    # --- training path: replay chronologically, exactly as build() does ---
    tracker = build_snapshots.HistoryTracker(HORIZON)
    compared = 0
    for n, pr in enumerate(prs, start=1):
        t0 = build_snapshots._parse_ts(pr["created_at"])
        expected = build_snapshots.features_at_t0(pr, t0, tracker)
        actual = feature_extractor.build_features_for_pr(db, n, COMPANY_ID, HORIZON)

        assert set(actual) == set(expected), f"column set differs on PR #{n}"
        for key in expected:
            assert actual[key] == pytest.approx(expected[key]) \
                if isinstance(expected[key], float) else actual[key] == expected[key], (
                f"PR #{n} feature {key}: {actual[key]!r} != {expected[key]!r}"
            )
        compared += 1

        resolved_at = (build_snapshots._parse_ts(pr.get("merged_at"))
                       or build_snapshots._parse_ts(pr.get("closed_at")))
        if resolved_at is not None:
            login = (pr.get("user") or {}).get("login") or "<unknown>"
            tracker.add(login, resolved_at,
                        (resolved_at - t0).total_seconds() / 86400.0)

    assert compared > 100, "parity check ran on too little data to be meaningful"


def test_feature_columns_match_the_trained_bundle():
    """The extractor must produce exactly the columns the model expects."""
    joblib = pytest.importorskip("joblib")
    bundle_path = SERVICE_ROOT / "training" / "stale_pr_model.pkl"
    if not bundle_path.exists():
        pytest.skip("trained bundle not present")
    bundle = joblib.load(bundle_path)
    assert set(bundle["feature_columns"]) == set(feature_extractor.FEATURE_COLUMNS)


def test_missing_pr_raises_not_found(db: Session):
    with pytest.raises(feature_extractor.PullRequestNotFound):
        feature_extractor.build_features_for_pr(db, 404, COMPANY_ID, HORIZON)


def test_other_companys_pr_is_not_visible(db: Session):
    db.add(_make_pr(1, company_id=2))
    db.commit()
    with pytest.raises(feature_extractor.PullRequestNotFound):
        feature_extractor.build_features_for_pr(db, 1, COMPANY_ID, HORIZON)
