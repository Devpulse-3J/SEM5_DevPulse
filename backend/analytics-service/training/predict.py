#!/usr/bin/env python3
"""Odin Eye ML — score PRs with the trained point-in-time model.

Answers, for a given pull request:

    "Standing at this PR's T0, what is the probability it is still
     unresolved HORIZON days later?"

Feature construction is IMPORTED from build_snapshots.features_at_t0 rather
than reimplemented. That is deliberate: a second copy of the feature logic is
the usual way a model that looked good offline starts making nonsense
predictions in production.

Usage
-----
    python predict.py --open            # score every still-open PR in the cache
    python predict.py --pr 98765        # score one PR by number
    python predict.py --json some_pr.json
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

try:
    import joblib
    import pandas as pd
except ImportError:
    sys.exit("Missing dependency. Run:  pip install pandas scikit-learn joblib")

from build_snapshots import CACHE_FILE, HistoryTracker, _parse_ts, features_at_t0


DEFAULT_MODEL = "stale_pr_model.pkl"


def build_history(prs: list[dict], horizon_days: int) -> HistoryTracker:
    """Replay every resolved PR into a tracker, in chronological order.

    Only PRs that actually resolved contribute, exactly as during training.
    """
    tracker = HistoryTracker(horizon_days)
    for pr in sorted(prs, key=lambda p: p["created_at"]):
        t0 = _parse_ts(pr["created_at"])
        resolved_at = _parse_ts(pr.get("merged_at")) or _parse_ts(pr.get("closed_at"))
        if t0 is not None and resolved_at is not None:
            author = ((pr.get("user") or {}).get("login")) or "<unknown>"
            tracker.add(author, resolved_at, (resolved_at - t0).total_seconds() / 86400.0)
    return tracker


def score(bundle: dict, tracker: HistoryTracker, prs: list[dict]) -> pd.DataFrame:
    rows, meta = [], []
    for pr in prs:
        t0 = _parse_ts(pr["created_at"])
        if t0 is None:
            continue
        rows.append(features_at_t0(pr, t0, tracker))
        meta.append(
            {
                "pr_number": pr.get("number"),
                "author": ((pr.get("user") or {}).get("login")) or "<unknown>",
                "title": (pr.get("title") or "")[:58],
            }
        )

    if not rows:
        sys.exit("No PRs to score.")

    X = pd.DataFrame(rows)[bundle["feature_columns"]]
    proba = bundle["pipeline"].predict_proba(X)[:, 1]

    out = pd.DataFrame(meta)
    out["stale_probability"] = proba.round(4)
    # Thresholds mirror the three risk_category values pr_predictions accepts.
    out["risk_category"] = pd.cut(
        proba, bins=[-0.01, 0.34, 0.67, 1.01], labels=["low", "medium", "high"]
    )
    return out.sort_values("stale_probability", ascending=False)


def main() -> int:
    p = argparse.ArgumentParser(description="Score PRs for staleness risk.")
    p.add_argument("--model", default=DEFAULT_MODEL)
    p.add_argument("--cache", default=CACHE_FILE)
    p.add_argument("--open", action="store_true", dest="open_only",
                   help="score every PR still open in the cache")
    p.add_argument("--pr", type=int, help="score a single PR by number")
    p.add_argument("--json", help="score a PR from a JSON file")
    p.add_argument("--top", type=int, default=20)
    args = p.parse_args()

    model_path = Path(args.model)
    if not model_path.exists():
        sys.exit(f"{model_path} not found. Run:  python train_snapshot_model.py")
    bundle = joblib.load(model_path)

    print(f"Model: {bundle['algorithm']} v{bundle['model_version']} | "
          f"horizon {bundle['horizon_days']}d | {bundle['snapshot_definition']}")

    cache = Path(args.cache)
    if not cache.exists():
        sys.exit(f"{cache} not found.")
    all_prs = [json.loads(l) for l in cache.open(encoding="utf-8") if l.strip()]
    tracker = build_history(all_prs, bundle["horizon_days"])

    if args.json:
        targets = [json.loads(Path(args.json).read_text(encoding="utf-8"))]
    elif args.pr is not None:
        targets = [pr for pr in all_prs if pr.get("number") == args.pr]
        if not targets:
            sys.exit(f"PR #{args.pr} is not in {cache}.")
    elif args.open_only:
        targets = [pr for pr in all_prs
                   if not (pr.get("merged_at") or pr.get("closed_at"))]
        print(f"Scoring {len(targets)} still-open PRs "
              "(these are exactly the ones whose outcome is genuinely unknown)")
    else:
        sys.exit("Choose one of --open, --pr N, or --json FILE.")

    result = score(bundle, tracker, targets)

    print("\n" + "=" * 78)
    print(f"{'PR':>8}  {'risk':<7}{'P(stale)':>9}  {'author':<18} title")
    print("-" * 78)
    for _, r in result.head(args.top).iterrows():
        print(f"{r.pr_number:>8}  {str(r.risk_category):<7}{r.stale_probability:>9.3f}"
              f"  {r.author[:17]:<18} {r.title}")
    print("=" * 78)

    if len(result) > 1:
        counts = result["risk_category"].value_counts()
        print(f"  high={int(counts.get('high', 0))}  "
              f"medium={int(counts.get('medium', 0))}  "
              f"low={int(counts.get('low', 0))}  of {len(result)} scored")
    return 0


if __name__ == "__main__":
    sys.exit(main())
