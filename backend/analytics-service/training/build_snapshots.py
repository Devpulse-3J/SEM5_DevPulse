#!/usr/bin/env python3
"""Odin Eye ML — point-in-time snapshot builder (leakage-free dataset).

Turns the raw PR cache into a supervised dataset that answers the real
question:

    "It is T0. Knowing only what was true at T0, will this PR still be
     unresolved N days from now?"

    T0 = created_at
     |
     |--- features: facts fixed at T0 + history of PRs that ALREADY finished
     |
     v
    [T0, T0 + HORIZON_DAYS]
     |
     v
    label: 1 if still unresolved at the end of the window, else 0


Why T0 = created_at
-------------------
GitHub's pulls endpoint returns only the CURRENT state of a PR. The cache has
no timeline/event history, so counters like `comments`, `commits`, `additions`
and `changed_files` are collection-time values — there is no way to recover
what they were on some earlier date. That makes "snapshot at created_at + 5
days" impossible to build honestly from this data.

The one instant whose state we CAN reconstruct is the moment the PR opened, so
that is the snapshot time. Every feature below is either immutable after
opening, or is computed from OTHER PRs that had already resolved before T0.

Censoring
---------
A PR that is still open and whose window has not fully elapsed has an UNKNOWN
outcome. It is excluded, never guessed. Assigning it "stale" is what corrupted
the previous dataset.

Usage
-----
    python build_snapshots.py
    python build_snapshots.py --horizon 7 --out pr_snapshots.csv
"""
from __future__ import annotations

import argparse
import bisect
import json
import statistics
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

try:
    import pandas as pd
except ImportError:
    sys.exit("Missing dependency. Run:  pip install pandas")


CACHE_FILE = "pr_cache.jsonl"
DEFAULT_OUT = "pr_snapshots.csv"
DEFAULT_HORIZON = 14

# Rolling window for repo-level history, in PRs (not days).
REPO_WINDOW = 200


def _parse_ts(value: str | None) -> datetime | None:
    """GitHub ISO-8601. Python 3.10 cannot parse the trailing 'Z' directly."""
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def load_cache(path: Path) -> list[dict]:
    if not path.exists():
        sys.exit(f"{path} not found. Run:  python collect_pr_data.py")
    prs = [json.loads(line) for line in path.open(encoding="utf-8") if line.strip()]
    if not prs:
        sys.exit(f"{path} is empty.")
    return prs


def label_for(
    t0: datetime,
    resolved_at: datetime | None,
    horizon: timedelta,
    data_cutoff: datetime,
) -> int | None:
    """Outcome observed strictly AFTER T0. None means censored (unknown).

    0 = resolved inside the window        (healthy)
    1 = still unresolved at window close  (stale)
    None = window has not fully elapsed in our data; we cannot know yet
    """
    deadline = t0 + horizon

    if resolved_at is not None:
        return 0 if resolved_at <= deadline else 1

    # Still open at collection time. We only know it survived the whole
    # window if the window actually closed before our data ends.
    if deadline <= data_cutoff:
        return 1
    return None


class HistoryTracker:
    """Author- and repo-level statistics using ONLY already-finished PRs.

    A prior PR contributes to a snapshot at T0 only if it had RESOLVED before
    T0. Merely being created earlier is not enough — its outcome would still
    have been unknown to an observer standing at T0.
    """

    def __init__(self, horizon_days: int) -> None:
        self.horizon_days = horizon_days
        # author -> list of (resolved_at, resolution_days), unsorted
        self._by_author: dict[str, list[tuple[datetime, float]]] = {}
        # global, kept sorted by resolved_at for bisect
        self._resolved_at: list[datetime] = []
        self._resolution_days: list[float] = []

    def add(self, author: str, resolved_at: datetime, resolution_days: float) -> None:
        self._by_author.setdefault(author, []).append((resolved_at, resolution_days))
        idx = bisect.bisect_left(self._resolved_at, resolved_at)
        self._resolved_at.insert(idx, resolved_at)
        self._resolution_days.insert(idx, resolution_days)

    def author_stats(self, author: str, t0: datetime) -> dict:
        past = [(r, d) for r, d in self._by_author.get(author, []) if r < t0]
        if not past:
            return {
                "author_prior_prs": 0,
                "author_prior_stale_rate": -1.0,   # sentinel: no history
                "author_prior_median_days": -1.0,
                "author_is_new": 1,
            }
        days = [d for _, d in past]
        stale = [1 for d in days if d > self.horizon_days]
        return {
            "author_prior_prs": len(past),
            "author_prior_stale_rate": len(stale) / len(past),
            "author_prior_median_days": statistics.median(days),
            "author_is_new": 0,
        }

    def repo_stats(self, t0: datetime) -> dict:
        cut = bisect.bisect_left(self._resolved_at, t0)
        window = self._resolution_days[max(0, cut - REPO_WINDOW):cut]
        if not window:
            return {"repo_recent_stale_rate": -1.0, "repo_recent_median_days": -1.0}
        stale = [1 for d in window if d > self.horizon_days]
        return {
            "repo_recent_stale_rate": len(stale) / len(window),
            "repo_recent_median_days": statistics.median(window),
        }


def features_at_t0(pr: dict, t0: datetime, tracker: "HistoryTracker") -> dict:
    """The single definition of a feature row, shared by training and inference.

    predict.py imports this function rather than reimplementing it, so the two
    paths cannot drift apart — a mismatch here is the classic cause of a model
    that scores well offline and misbehaves in production.

    Every value below is knowable standing at T0 and nothing else.
    """
    author = ((pr.get("user") or {}).get("login")) or "<unknown>"
    row = {
        # Immutable properties of the PR as opened.
        "title_length": len(pr.get("title") or ""),
        "body_length": len(pr.get("body") or ""),
        "body_is_empty": int(not (pr.get("body") or "").strip()),
        "base_branch": (pr.get("base") or {}).get("ref"),
        "author_association": pr.get("author_association"),
        "created_hour": t0.hour,
        "created_weekday": t0.weekday(),
        "created_is_weekend": int(t0.weekday() >= 5),
    }
    row.update(tracker.author_stats(author, t0))
    row.update(tracker.repo_stats(t0))
    return row


def build(prs: list[dict], horizon_days: int) -> tuple[pd.DataFrame, dict]:
    horizon = timedelta(days=horizon_days)

    # The last moment our data can speak to. Anything needing knowledge past
    # this point is censored.
    data_cutoff = max(
        _parse_ts(p.get("updated_at")) or _parse_ts(p["created_at"]) for p in prs
    )

    # Chronological order is mandatory: history features must only ever see
    # the past, so PRs are processed in the order they were created.
    prs = sorted(prs, key=lambda p: p["created_at"])

    tracker = HistoryTracker(horizon_days)
    rows: list[dict] = []
    censored = 0

    for pr in prs:
        t0 = _parse_ts(pr["created_at"])
        if t0 is None:
            continue
        resolved_at = _parse_ts(pr.get("merged_at")) or _parse_ts(pr.get("closed_at"))
        author = ((pr.get("user") or {}).get("login")) or "<unknown>"

        label = label_for(t0, resolved_at, horizon, data_cutoff)

        if label is None:
            censored += 1
        else:
            # ---- FEATURES: every one must be knowable standing at T0 ----
            row = {
                "pr_number": pr.get("number"),
                "snapshot_at": t0.isoformat(),
                "author": author,
            }
            row.update(features_at_t0(pr, t0, tracker))
            # Outcome, kept for auditing only. Dropped before training.
            row["_resolved_at"] = resolved_at.isoformat() if resolved_at else ""
            row["stale"] = label
            rows.append(row)

        # Only AFTER emitting this row does the PR's own outcome become
        # available to later snapshots — and only if it actually resolved.
        if resolved_at is not None:
            tracker.add(author, resolved_at, (resolved_at - t0).total_seconds() / 86400.0)

    df = pd.DataFrame(rows)
    stats = {
        "original_prs": len(prs),
        "valid_snapshots": len(df),
        "censored_excluded": censored,
        "data_cutoff": data_cutoff,
        "horizon_days": horizon_days,
    }
    return df, stats


def report(df: pd.DataFrame, stats: dict) -> None:
    print("\n" + "=" * 62)
    print("DATASET STATISTICS")
    print("=" * 62)
    print(f"  Original PRs in cache      : {stats['original_prs']}")
    print(f"  Valid snapshots            : {stats['valid_snapshots']}")
    print(f"  Excluded (outcome unknown) : {stats['censored_excluded']}")
    print(f"  Prediction horizon         : {stats['horizon_days']} days")
    print(f"  Data cutoff                : {stats['data_cutoff'].date()}")

    snap = pd.to_datetime(df["snapshot_at"], utc=True, format="mixed")
    print(f"  Snapshot date range        : {snap.min().date()} -> {snap.max().date()}")

    pos = int(df["stale"].sum())
    neg = len(df) - pos
    print(f"\n  Positive (stale = 1)       : {pos}  ({100 * pos / len(df):.1f}%)")
    print(f"  Negative (resolved  = 0)   : {neg}  ({100 * neg / len(df):.1f}%)")
    print(f"  Majority-class baseline    : {100 * max(pos, neg) / len(df):.1f}% accuracy")
    print("=" * 62)


def main() -> int:
    p = argparse.ArgumentParser(description="Build a point-in-time PR dataset.")
    p.add_argument("--cache", default=CACHE_FILE)
    p.add_argument("--out", default=DEFAULT_OUT)
    p.add_argument("--horizon", type=int, default=DEFAULT_HORIZON,
                   help=f"prediction window in days (default: {DEFAULT_HORIZON})")
    args = p.parse_args()

    print("=" * 62)
    print(f"Odin Eye ML — point-in-time snapshots (T0 = created_at, "
          f"horizon = {args.horizon}d)")
    print("=" * 62)

    prs = load_cache(Path(args.cache))
    print(f"  Loaded {len(prs)} PRs from {args.cache}")

    df, stats = build(prs, args.horizon)
    df.to_csv(args.out, index=False)
    print(f"  Wrote {len(df)} snapshots x {len(df.columns)} columns -> {args.out}")

    report(df, stats)
    print("\nNext:  python train_snapshot_model.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
