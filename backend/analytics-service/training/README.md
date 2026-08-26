# Odin Eye ML - Stale PR Detection

Collects real pull-request history from GitHub and trains a classifier that
scores a PR's **staleness risk** as `Low`, `Medium`, or `High`.

This is the training half of DevPulse's `analytics-service`. Training is a
dev-time task — the service's Docker image only copies `app/`, so nothing in
this folder ships to production. Only the serialised model does.

---

## Quick start

```bash
# 1. Install the dependencies
pip install requests pandas scikit-learn joblib python-dotenv

# 2. Add your GitHub token
cp .env.example .env        # then paste your token into .env

# 3. Collect data, then train
python collect_pr_data.py --max-prs 500    # start small to verify the setup
python train_model.py
```

Once the small run works, do the full one: `python collect_pr_data.py`.

---

## Installation

Requires **Python 3.10+**.

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install requests pandas scikit-learn joblib python-dotenv
```

### GitHub token

1. Go to <https://github.com/settings/tokens>.
2. Generate a **classic** token with the `public_repo` scope (that is all you
   need for public repositories).
3. `cp .env.example .env` and paste the token in, or `export GITHUB_TOKEN=...`.

A token raises your limit from **60 requests/hour** (anonymous) to
**5000 requests/hour**. The collector cannot do useful work without one.

---

## Usage

### Collect

```bash
python collect_pr_data.py                     # 5000 PRs from tensorflow/tensorflow
python collect_pr_data.py --max-prs 500       # quick sample
python collect_pr_data.py --repo facebook/react
python collect_pr_data.py --no-details        # fast, fewer features
```

**Budget the time.** GitHub's list endpoint returns 100 PRs per request but
omits `additions`, `deletions`, `changed_files` and `commits`. Those need one
extra request *per PR*, so a full 5000-PR run costs ~5050 requests — your
entire hourly quota — and takes **30-60 minutes**.

The script handles this for you:

- pauses automatically when the remaining quota runs low, and resumes at reset
- appends every fetched PR to `pr_cache.jsonl`, so an interrupted run picks up
  where it stopped instead of re-spending the quota
- prints throughput and an ETA every 50 PRs

`--no-details` skips the per-PR phase entirely: ~50 requests and under a
minute, at the cost of the diff-size features.

### Train

```bash
python train_model.py
```

Prints 5-fold cross-validated macro F1, a test-set classification report, a
confusion matrix, and the top 15 features. Writes `stale_pr_model.pkl`.

---

## What you get

### `pr_dataset.csv`

One row per pull request.

| Column | Meaning |
|---|---|
| `pr_size` | `additions + deletions` |
| `additions`, `deletions` | lines added / removed |
| `files_touched` | files changed |
| `num_commits` | commits in the PR |
| `title_length`, `body_length` | description effort |
| `is_draft` | opened as a draft |
| `author_association` | `MEMBER`, `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR`, … |
| `num_labels`, `num_requested_reviewers` | triage signals |
| `base_branch` | target branch |
| `created_hour`, `created_weekday` | when it was opened |
| `num_comments`, `num_review_comments` | discussion volume |
| `time_since_activity` | days since the last update — **see the warning below** |
| `resolution_days` | days from creation to merge/close (or to now, if open) |
| `staleness_risk` | **the label**: `Low` / `Medium` / `High` |

### The label

`staleness_risk` buckets `resolution_days`:

| Bucket | Time to merge or close |
|---|---|
| `Low` | under 3 days |
| `Medium` | 3 to 14 days |
| `High` | over 14 days, or still open past 14 days |

Thresholds are the `STALE_MEDIUM_DAYS` / `STALE_HIGH_DAYS` constants at the top
of `collect_pr_data.py`.

### Leakage warning

`resolution_days` **is** the label, and `time_since_activity` is measured after
the fact for PRs that are already closed. Training on either gives near-perfect
scores that collapse on real open PRs. `train_model.py` drops both by default —
the `--keep-leaky` flag exists only to demonstrate the effect, never to report
a score.

`time_since_activity` is still genuinely useful at *inference* time for a PR
that is currently open, which is why it is collected.

### `stale_pr_model.pkl`

A joblib bundle: the fitted sklearn pipeline plus `labels`, `feature_columns`,
`algorithm`, `model_version`, and `prediction_type` — the fields the shared
`pr_predictions` table requires.

Both `*.csv` and `*.pkl` are gitignored: they are regenerable outputs, not
source. For larger or shared datasets, follow the conventions in
[`../data/README.md`](../data/README.md) — raw dumps in `data/raw/`, a small
committed slice in `data/samples/`.
