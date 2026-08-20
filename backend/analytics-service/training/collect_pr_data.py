#!/usr/bin/env python3
"""Odin Eye ML — GitHub PR data collector.

Pulls pull requests from a GitHub repository and turns them into a flat
feature table for staleness / risk modelling.

How it works
------------
1. LIST phase   — pages `/repos/{owner}/{repo}/pulls?state=all` (100 PRs per
                  request). Cheap: ~50 requests for 5000 PRs.
2. DETAIL phase — the list endpoint does NOT return `additions`, `deletions`,
                  `changed_files` or `commits`, so each PR needs its own
                  `/pulls/{number}` request. That is 1 request PER PR, i.e.
                  ~5000 requests, which is the entire hourly rate limit for an
                  authenticated token. Expect 30-60 minutes for a full run.
                  Use --no-details for a fast run without diff-size features.

Every fetched PR is appended to a JSONL cache so an interrupted run resumes
instead of re-spending the rate limit.

Usage
-----
    export GITHUB_TOKEN=ghp_xxx
    python collect_pr_data.py                       # 5000 PRs, tensorflow/tensorflow
    python collect_pr_data.py --max-prs 500         # quick sample
    python collect_pr_data.py --repo facebook/react
    python collect_pr_data.py --no-details          # fast, fewer features
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

try:
    import requests
except ImportError:
    sys.exit("Missing dependency. Run:  pip install requests pandas python-dotenv")

try:
    import pandas as pd
except ImportError:
    sys.exit("Missing dependency. Run:  pip install requests pandas python-dotenv")

# python-dotenv is optional: if present, a local .env is loaded automatically.
try:
    from dotenv import load_dotenv

    load_dotenv()
except ImportError:
    pass


# --------------------------------------------------------------------------
# Configuration
# --------------------------------------------------------------------------
DEFAULT_REPO = "tensorflow/tensorflow"
DEFAULT_MAX_PRS = 5000
DEFAULT_OUT = "pr_dataset.csv"
CACHE_FILE = "pr_cache.jsonl"

API_ROOT = "https://api.github.com"
PAGE_SIZE = 100

# Stop and wait once the remaining request budget drops this low.
RATE_LIMIT_FLOOR = 50

# Staleness buckets, measured in days from PR creation to resolution
# (merge/close), or to now for a PR that is still open.
STALE_MEDIUM_DAYS = 3
STALE_HIGH_DAYS = 14


# --------------------------------------------------------------------------
# HTTP helpers
# --------------------------------------------------------------------------
def build_session(token: str) -> requests.Session:
    session = requests.Session()
    session.headers.update(
        {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "odin-eye-ml-collector",
            "Authorization": f"Bearer {token}",
        }
    )
    return session


def _sleep_until(reset_epoch: float, why: str) -> None:
    wait = max(0.0, reset_epoch - time.time()) + 5
    mins = wait / 60
    print(f"   [rate limit] {why} — sleeping {mins:.1f} min until reset...")
    time.sleep(wait)


def get_json(session: requests.Session, url: str, params: dict | None = None):
    """GET with rate-limit awareness and retry on transient failures."""
    for attempt in range(1, 6):
        try:
            resp = session.get(url, params=params, timeout=30)
        except requests.RequestException as exc:
            print(f"   [warn] network error ({exc}); retry {attempt}/5")
            time.sleep(2 * attempt)
            continue

        # Primary rate limit exhausted.
        if resp.status_code == 403 and resp.headers.get("X-RateLimit-Remaining") == "0":
            _sleep_until(float(resp.headers.get("X-RateLimit-Reset", time.time() + 60)),
                         "hourly quota exhausted")
            continue

        # Secondary / abuse rate limit.
        if resp.status_code in (403, 429):
            retry_after = float(resp.headers.get("Retry-After", 60))
            print(f"   [rate limit] secondary limit — sleeping {retry_after:.0f}s")
            time.sleep(retry_after)
            continue

        if resp.status_code == 404:
            return None

        if resp.status_code >= 500:
            print(f"   [warn] GitHub {resp.status_code}; retry {attempt}/5")
            time.sleep(2 * attempt)
            continue

        if resp.status_code != 200:
            raise RuntimeError(f"GitHub {resp.status_code} for {url}: {resp.text[:200]}")

        # Proactively pause before we hit the wall.
        remaining = int(resp.headers.get("X-RateLimit-Remaining", "9999"))
        if remaining <= RATE_LIMIT_FLOOR:
            _sleep_until(float(resp.headers.get("X-RateLimit-Reset", time.time() + 60)),
                         f"only {remaining} requests left")

        return resp.json()

    raise RuntimeError(f"Gave up on {url} after 5 attempts")


# --------------------------------------------------------------------------
# Collection
# --------------------------------------------------------------------------
def list_pull_requests(session: requests.Session, repo: str, max_prs: int) -> list[dict]:
    """Page the pulls list endpoint until max_prs is reached."""
    print(f"\n[1/3] Listing pull requests from {repo}...")
    url = f"{API_ROOT}/repos/{repo}/pulls"
    collected: list[dict] = []
    page = 1

    while len(collected) < max_prs:
        batch = get_json(
            session,
            url,
            params={
                "state": "all",
                "sort": "created",
                "direction": "desc",
                "per_page": PAGE_SIZE,
                "page": page,
            },
        )
        if not batch:
            print("   Reached the end of the repository's pull requests.")
            break

        collected.extend(batch)
        print(f"   page {page:>3}  |  {len(collected):>5} / {max_prs} PRs listed")
        page += 1

    collected = collected[:max_prs]
    print(f"   Done. {len(collected)} pull requests listed.")
    return collected


def load_cache(path: Path) -> dict[int, dict]:
    if not path.exists():
        return {}
    cache: dict[int, dict] = {}
    with path.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                pr = json.loads(line)
            except json.JSONDecodeError:
                continue
            cache[pr["number"]] = pr
    if cache:
        print(f"   Resuming: {len(cache)} PRs already in {path.name}")
    return cache


def fetch_details(
    session: requests.Session, repo: str, listed: list[dict], cache_path: Path
) -> list[dict]:
    """Fetch per-PR detail (additions/deletions/changed_files/commits)."""
    print(f"\n[2/3] Fetching per-PR details (1 request each — this is the slow part)")
    cache = load_cache(cache_path)
    todo = [pr for pr in listed if pr["number"] not in cache]

    if not todo:
        print("   Everything already cached; skipping network calls.")
        return [cache[pr["number"]] for pr in listed]

    print(f"   {len(todo)} PRs to fetch, {len(cache)} cached.")
    est_min = len(todo) * 0.8 / 60
    print(f"   Rough estimate: {est_min:.0f} min (GitHub allows 5000 req/hour).")

    started = time.time()
    with cache_path.open("a", encoding="utf-8") as fh:
        for i, stub in enumerate(todo, start=1):
            number = stub["number"]
            detail = get_json(session, f"{API_ROOT}/repos/{repo}/pulls/{number}")
            if detail is None:
                continue

            cache[number] = detail
            fh.write(json.dumps(detail) + "\n")
            fh.flush()

            if i % 50 == 0 or i == len(todo):
                elapsed = time.time() - started
                rate = i / elapsed if elapsed else 0
                eta = (len(todo) - i) / rate / 60 if rate else 0
                print(
                    f"   {i:>5} / {len(todo)}  |  {rate:.1f} PR/s  |  ETA {eta:.0f} min"
                )

    return [cache[pr["number"]] for pr in listed if pr["number"] in cache]


# --------------------------------------------------------------------------
# Feature engineering
# --------------------------------------------------------------------------
def _parse_ts(value: str | None) -> datetime | None:
    """Parse GitHub's ISO-8601 timestamps.

    Python 3.10's fromisoformat cannot handle the trailing 'Z', so normalise it.
    """
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def _days_between(later: datetime | None, earlier: datetime | None) -> float | None:
    if later is None or earlier is None:
        return None
    return round((later - earlier).total_seconds() / 86400.0, 4)


def risk_bucket(resolution_days: float | None) -> str:
    """Map time-to-resolution onto the Low / Medium / High staleness label."""
    if resolution_days is None:
        return "Unknown"
    if resolution_days < STALE_MEDIUM_DAYS:
        return "Low"
    if resolution_days < STALE_HIGH_DAYS:
        return "Medium"
    return "High"


def build_features(prs: list[dict], repo: str, with_details: bool) -> pd.DataFrame:
    print("\n[3/3] Building the feature table...")
    now = datetime.now(timezone.utc)
    rows: list[dict] = []

    for pr in prs:
        created = _parse_ts(pr.get("created_at"))
        updated = _parse_ts(pr.get("updated_at"))
        merged = _parse_ts(pr.get("merged_at"))
        closed = _parse_ts(pr.get("closed_at"))

        resolved_at = merged or closed
        is_open = resolved_at is None

        # Label basis: how long the PR took to resolve, or how long it has
        # been sitting open so far.
        resolution_days = _days_between(resolved_at or now, created)

        # --- temporal features measured from NOW (never from a future event) ---
        # Both are knowable at prediction time for a live PR, which is what
        # makes them usable as inputs.
        #
        # CAUTION, time_since_created: for an OPEN PR, resolution_days above is
        # itself (now - created), so this column and the label are the same
        # quantity. On open rows it does not predict staleness — it *is*
        # staleness, restated. See the leakage note in train_model.py.
        time_since_created = (now - created).days if created else None
        time_since_last_activity = (now - updated).days if updated else None

        additions = pr.get("additions")
        deletions = pr.get("deletions")
        pr_size = (
            (additions or 0) + (deletions or 0)
            if additions is not None or deletions is not None
            else None
        )

        rows.append(
            {
                "repo": repo,
                "pr_number": pr.get("number"),
                # --- features available the moment a PR is opened ---
                "pr_size": pr_size,
                "additions": additions,
                "deletions": deletions,
                "files_touched": pr.get("changed_files"),
                "num_commits": pr.get("commits"),
                "title_length": len(pr.get("title") or ""),
                "body_length": len(pr.get("body") or ""),
                "is_draft": bool(pr.get("draft", False)),
                "author_association": pr.get("author_association"),
                "num_labels": len(pr.get("labels") or []),
                "num_requested_reviewers": len(pr.get("requested_reviewers") or []),
                "base_branch": (pr.get("base") or {}).get("ref"),
                # --- temporal, measured from now ---
                "time_since_created": time_since_created,
                "time_since_last_activity": time_since_last_activity,
                "created_hour": created.hour if created else None,
                "created_weekday": created.weekday() if created else None,
                # --- activity signals ---
                "num_comments": pr.get("comments"),
                "num_review_comments": pr.get("review_comments"),
                # NOTE: for a CLOSED PR this is measured after the fact and
                # leaks the outcome. train_model.py drops it by default; it is
                # only legitimately known at inference time for OPEN PRs.
                "time_since_activity": _days_between(resolved_at or now, updated),
                # --- outcome ---
                "state": pr.get("state"),
                "is_merged": merged is not None,
                "is_open": is_open,
                "resolution_days": resolution_days,
                "staleness_risk": risk_bucket(resolution_days),
                "created_at": pr.get("created_at"),
                "updated_at": pr.get("updated_at"),
                "closed_at": pr.get("closed_at"),
                "merged_at": pr.get("merged_at"),
            }
        )

    df = pd.DataFrame(rows)

    if not with_details:
        # These columns are only populated by the per-PR detail request.
        detail_only = [
            "pr_size", "additions", "deletions", "files_touched",
            "num_commits", "body_length", "num_comments", "num_review_comments",
        ]
        df = df.drop(columns=[c for c in detail_only if c in df.columns])
        print("   --no-details: diff-size features omitted.")

    return df


def show_distribution(df: pd.DataFrame) -> None:
    print("\n" + "=" * 58)
    print("STALENESS DISTRIBUTION")
    print("=" * 58)

    counts = df["staleness_risk"].value_counts()
    total = len(df)
    for label in ("Low", "Medium", "High", "Unknown"):
        n = int(counts.get(label, 0))
        if n == 0 and label == "Unknown":
            continue
        pct = 100.0 * n / total if total else 0.0
        bar = "#" * int(pct / 2)
        print(f"  {label:<8} {n:>6}  ({pct:5.1f}%)  {bar}")

    print("-" * 58)
    print(f"  {'TOTAL':<8} {total:>6}")
    print()
    print(f"  Buckets: Low < {STALE_MEDIUM_DAYS}d, "
          f"Medium {STALE_MEDIUM_DAYS}-{STALE_HIGH_DAYS}d, "
          f"High > {STALE_HIGH_DAYS}d (time to merge/close)")
    print(f"  Open PRs   : {int(df['is_open'].sum())}")
    print(f"  Merged PRs : {int(df['is_merged'].sum())}")

    if "pr_size" in df.columns and df["pr_size"].notna().any():
        print(f"  Median PR size  : {df['pr_size'].median():.0f} lines changed")
        print(f"  Median files    : {df['files_touched'].median():.0f}")
    print("=" * 58)


# --------------------------------------------------------------------------
# Entry point
# --------------------------------------------------------------------------
def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Collect GitHub PR data for staleness modelling.")
    p.add_argument("--repo", default=DEFAULT_REPO, help=f"owner/name (default: {DEFAULT_REPO})")
    p.add_argument("--max-prs", type=int, default=DEFAULT_MAX_PRS,
                   help=f"how many PRs to collect (default: {DEFAULT_MAX_PRS})")
    p.add_argument("--out", default=DEFAULT_OUT, help=f"output CSV (default: {DEFAULT_OUT})")
    p.add_argument("--no-details", action="store_true",
                   help="skip per-PR requests: much faster, but no diff-size features")
    return p.parse_args()


def main() -> int:
    args = parse_args()

    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        print("ERROR: GITHUB_TOKEN is not set.\n")
        print("  1. Create a token: https://github.com/settings/tokens")
        print("     (a classic token with 'public_repo' scope is enough)")
        print("  2. cp .env.example .env   and paste it in,")
        print("     or:  export GITHUB_TOKEN=ghp_your_token_here\n")
        print("Without a token GitHub allows only 60 requests/hour — far too few.")
        return 1

    print("=" * 58)
    print("Odin Eye ML — PR data collector")
    print("=" * 58)
    print(f"  Repository : {args.repo}")
    print(f"  Target     : {args.max_prs} pull requests")
    print(f"  Output     : {args.out}")
    print(f"  Detail mode: {'off (fast)' if args.no_details else 'on (full features)'}")

    session = build_session(token)

    limit = get_json(session, f"{API_ROOT}/rate_limit")
    if limit:
        core = limit["resources"]["core"]
        print(f"  Rate limit : {core['remaining']} / {core['limit']} requests left this hour")

    listed = list_pull_requests(session, args.repo, args.max_prs)
    if not listed:
        print("No pull requests found — check the repo name.")
        return 1

    if args.no_details:
        prs = listed
    else:
        prs = fetch_details(session, args.repo, listed, Path(CACHE_FILE))

    df = build_features(prs, args.repo, with_details=not args.no_details)

    out_path = Path(args.out)
    df.to_csv(out_path, index=False)
    print(f"\nSaved {len(df)} rows x {len(df.columns)} columns -> {out_path}")

    show_distribution(df)

    print("\nNext step:")
    print("   python train_model.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
