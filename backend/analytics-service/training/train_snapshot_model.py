#!/usr/bin/env python3
"""Odin Eye ML — train the stale-PR model on point-in-time snapshots.

Consumes pr_snapshots.csv (from build_snapshots.py) and trains a binary
classifier for:

    "Standing at T0, will this PR still be unresolved HORIZON days later?"

Everything here is built around one rule: nothing observed after T0 may reach
the model. That constrains three things people usually get wrong —

  1. Features        every column is a fact fixed at T0 (audited below).
  2. The split       chronological, never random. A random split would let
                     the model train on August and be tested on June, which
                     is not a situation that can ever occur in production.
  3. Preprocessing   imputation/encoding are fitted inside the Pipeline on
                     the TRAIN fold only, so test-period statistics never
                     influence the transforms.

Usage
-----
    python train_snapshot_model.py
    python train_snapshot_model.py --data pr_snapshots.csv --out stale_pr_model.pkl
    python train_snapshot_model.py --audit-only
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

try:
    import joblib
    import numpy as np
    import pandas as pd
    from sklearn.compose import ColumnTransformer
    from sklearn.dummy import DummyClassifier
    from sklearn.ensemble import RandomForestClassifier
    from sklearn.impute import SimpleImputer
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import (
        accuracy_score,
        average_precision_score,
        classification_report,
        confusion_matrix,
        f1_score,
        precision_score,
        recall_score,
        roc_auc_score,
    )
    from sklearn.pipeline import Pipeline
    from sklearn.preprocessing import OneHotEncoder, StandardScaler
    from sklearn.utils.class_weight import compute_sample_weight
except ImportError:
    sys.exit("Missing dependency. Run:  pip install pandas scikit-learn joblib")


TARGET = "stale"
SNAPSHOT_COL = "snapshot_at"

# Never features: identifiers and the audit trail.
NON_FEATURES = ["pr_number", "snapshot_at", "author", "_resolved_at", TARGET]

CATEGORICAL = ["base_branch", "author_association"]

# Chronological split points.
TRAIN_FRAC, VAL_FRAC = 0.70, 0.15

# Every feature, with the justification that keeps it in. Anything that cannot
# be defended here does not belong in the model.
LEAKAGE_AUDIT = [
    ("title_length",             False, "Fixed when the PR is opened"),
    ("body_length",              False, "Fixed when the PR is opened"),
    ("body_is_empty",            False, "Fixed when the PR is opened"),
    ("base_branch",              False, "Chosen at open time"),
    ("author_association",       False, "Author's standing at open time"),
    ("created_hour",             False, "Derived from created_at only"),
    ("created_weekday",          False, "Derived from created_at only"),
    ("created_is_weekend",       False, "Derived from created_at only"),
    ("author_prior_prs",         False, "Counts only PRs RESOLVED before T0"),
    ("author_prior_stale_rate",  False, "Counts only PRs RESOLVED before T0"),
    ("author_prior_median_days", False, "Counts only PRs RESOLVED before T0"),
    ("author_is_new",            False, "Derived from the same prior-only history"),
    ("repo_recent_stale_rate",   False, "Rolling window of PRs RESOLVED before T0"),
    ("repo_recent_median_days",  False, "Rolling window of PRs RESOLVED before T0"),
]

# Removed from the legacy pipeline, with the reason. Kept in the source so the
# decision is reviewable rather than folklore.
REMOVED_FEATURES = [
    ("resolution_days",          True,  "The old label was this value, bucketed"),
    ("time_since_created",       True,  "== resolution_days for open PRs (r=0.9999)"),
    ("time_since_activity",      True,  "Computed from resolved_at, a future event"),
    ("time_since_last_activity", True,  "For closed PRs ~= now - closed_at"),
    ("state",                    True,  "Final status; unknown at T0"),
    ("is_merged",                True,  "Outcome itself"),
    ("is_open",                  True,  "Outcome itself"),
    ("closed_at",                True,  "Future timestamp"),
    ("merged_at",                True,  "Future timestamp"),
    ("num_comments",             True,  "Accumulates AFTER T0; long life -> more"),
    ("num_review_comments",      True,  "Accumulates AFTER T0"),
    ("num_commits",              True,  "Commits pushed AFTER T0"),
    ("additions",                True,  "Diff grows as commits land AFTER T0"),
    ("deletions",                True,  "Diff grows as commits land AFTER T0"),
    ("pr_size",                  True,  "Sum of additions/deletions; same issue"),
    ("files_touched",            True,  "Grows as commits land AFTER T0"),
    ("num_labels",               True,  "Triage happens AFTER T0"),
    ("num_requested_reviewers",  True,  "Reviewers assigned AFTER T0"),
    ("is_draft",                 True,  "Collection-time status, not open-time"),
    ("mergeable",                True,  "Recomputed by GitHub continuously"),
]


def print_audit() -> None:
    print("\n" + "=" * 78)
    print("LEAKAGE AUDIT")
    print("=" * 78)
    print(f"  {'Feature':<26} {'Leakage?':<10} Reason")
    print("  " + "-" * 74)
    for name, leaky, reason in LEAKAGE_AUDIT:
        print(f"  {name:<26} {'YES' if leaky else 'No':<10} {reason}")
    print(f"\n  REMOVED from the previous pipeline ({len(REMOVED_FEATURES)} columns):")
    print("  " + "-" * 74)
    for name, leaky, reason in REMOVED_FEATURES:
        print(f"  {name:<26} {'YES' if leaky else 'No':<10} {reason}")
    print("\n  Preprocessing check: imputation, scaling and one-hot encoding all")
    print("  live INSIDE the Pipeline, so they are fitted on the training fold")
    print("  only — no test-period statistics leak in through the transforms.")
    print("  History features are causal by construction: a prior PR counts")
    print("  only once it has RESOLVED, which is strictly before T0.")
    print("=" * 78)


def temporal_split(df: pd.DataFrame):
    """Oldest snapshots train, newest test. Never random."""
    df = df.sort_values(SNAPSHOT_COL).reset_index(drop=True)
    n = len(df)
    i_train, i_val = int(n * TRAIN_FRAC), int(n * (TRAIN_FRAC + VAL_FRAC))
    return df.iloc[:i_train], df.iloc[i_train:i_val], df.iloc[i_val:]


def build_pipeline(X: pd.DataFrame, algorithm: str) -> Pipeline:
    categorical = [c for c in CATEGORICAL if c in X.columns]
    numeric = [c for c in X.columns if c not in categorical]

    # Heterogeneous by design; annotated so type checkers accept the append.
    numeric_steps: list[tuple[str, Any]] = [
        ("impute", SimpleImputer(strategy="median"))
    ]
    if algorithm == "logistic_regression":
        numeric_steps.append(("scale", StandardScaler(with_mean=False)))

    pre = ColumnTransformer(
        [
            ("num", Pipeline(numeric_steps), numeric),
            ("cat", OneHotEncoder(handle_unknown="ignore", min_frequency=10), categorical),
        ],
        remainder="drop",
    )

    if algorithm == "majority":
        clf: Any = DummyClassifier(strategy="most_frequent")
    elif algorithm == "logistic_regression":
        clf = LogisticRegression(max_iter=2000, class_weight="balanced", random_state=42)
    elif algorithm == "random_forest":
        clf = RandomForestClassifier(
            n_estimators=400, max_depth=10, min_samples_leaf=10,
            class_weight="balanced", random_state=42, n_jobs=-1,
        )
    else:
        try:
            from xgboost import XGBClassifier
        except ImportError:
            sys.exit("xgboost is not installed. Run:  pip install xgboost")
        clf = XGBClassifier(
            n_estimators=400, learning_rate=0.05, max_depth=5,
            min_child_weight=10, subsample=0.8, colsample_bytree=0.8,
            reg_lambda=1.0, tree_method="hist", eval_metric="logloss",
            random_state=42, n_jobs=-1,
        )

    return Pipeline([("prep", pre), ("clf", clf)])


def fit(pipeline: Pipeline, X, y, algorithm: str) -> Pipeline:
    # XGBoost has no class_weight; balancing goes through sample weights.
    if algorithm == "xgboost":
        pipeline.fit(X, y, clf__sample_weight=compute_sample_weight("balanced", y))
    else:
        pipeline.fit(X, y)
    return pipeline


def evaluate(pipeline: Pipeline, X, y) -> dict:
    pred = pipeline.predict(X)
    try:
        proba = pipeline.predict_proba(X)[:, 1]
    except (AttributeError, IndexError):
        proba = None

    out = {
        "accuracy": accuracy_score(y, pred),
        "precision": precision_score(y, pred, zero_division=0),
        "recall": recall_score(y, pred, zero_division=0),
        "f1": f1_score(y, pred, zero_division=0),
        "roc_auc": float("nan"),
        "pr_auc": float("nan"),
    }
    if proba is not None and len(set(y)) > 1:
        out["roc_auc"] = roc_auc_score(y, proba)
        out["pr_auc"] = average_precision_score(y, proba)
    return out


def show_importance(pipeline: Pipeline, algorithm: str) -> None:
    try:
        names = list(pipeline.named_steps["prep"].get_feature_names_out())
    except Exception:
        return
    clf = pipeline.named_steps["clf"]

    values = getattr(clf, "feature_importances_", None)
    kind = "importance"
    if values is None:
        if not hasattr(clf, "coef_"):
            return
        values = np.abs(clf.coef_).ravel()
        kind = "|coefficient|"

    if len(names) != len(values):
        return

    total = values.sum() or 1.0
    print(f"\n  Feature {kind} ({algorithm}) — top 15")
    for rank, i in enumerate(np.argsort(values)[::-1][:15], start=1):
        share = values[i] / total
        print(f"    {rank:>2}. {names[i]:<40} {share:.4f} {'#' * int(share * 60)}")
    top = values.max() / total
    if top > 0.40:
        print(f"\n    WARNING: one feature holds {top:.0%} of total importance.")
        print("    Check it for leakage before trusting these numbers.")


def main() -> int:
    p = argparse.ArgumentParser(description="Train on point-in-time snapshots.")
    p.add_argument("--data", default="pr_snapshots.csv")
    p.add_argument("--out", default="stale_pr_model.pkl")
    p.add_argument("--audit-only", action="store_true", help="print the audit and exit")
    args = p.parse_args()

    print("=" * 78)
    print("Odin Eye ML — leakage-free training on point-in-time snapshots")
    print("=" * 78)

    print_audit()
    if args.audit_only:
        return 0

    path = Path(args.data)
    if not path.exists():
        sys.exit(f"{path} not found. Run:  python build_snapshots.py")
    df = pd.read_csv(path)

    train, val, test = temporal_split(df)
    print("\n" + "=" * 78)
    print("TEMPORAL SPLIT (chronological — no random shuffling)")
    print("=" * 78)
    for name, part in (("train", train), ("val", val), ("test", test)):
        s = pd.to_datetime(part[SNAPSHOT_COL], utc=True, format="mixed")
        print(f"  {name:<6} {len(part):>5} rows | {s.min().date()} -> {s.max().date()}"
              f" | stale {100 * part[TARGET].mean():.1f}%")

    feats = [c for c in df.columns if c not in NON_FEATURES]
    print(f"\n  Features: {len(feats)}")
    print("   ", ", ".join(feats))

    Xtr, ytr = train[feats], train[TARGET].to_numpy()
    Xva, yva = val[feats], val[TARGET].to_numpy()
    Xte, yte = test[feats], test[TARGET].to_numpy()

    algorithms = ["majority", "logistic_regression", "random_forest", "xgboost"]
    results, fitted = {}, {}

    print("\n" + "=" * 78)
    print("MODEL COMPARISON (validation set — used for selection)")
    print("=" * 78)
    print(f"  {'model':<22}{'acc':>8}{'prec':>8}{'recall':>8}{'F1':>8}{'ROC-AUC':>10}{'PR-AUC':>9}")
    print("  " + "-" * 71)
    for algo in algorithms:
        pipe = fit(build_pipeline(Xtr, algo), Xtr, ytr, algo)
        m = evaluate(pipe, Xva, yva)
        results[algo], fitted[algo] = m, pipe
        print(f"  {algo:<22}{m['accuracy']:>8.3f}{m['precision']:>8.3f}"
              f"{m['recall']:>8.3f}{m['f1']:>8.3f}{m['roc_auc']:>10.3f}{m['pr_auc']:>9.3f}")

    # Select on validation F1, ignoring the constant baseline.
    best = max((a for a in algorithms if a != "majority"),
               key=lambda a: results[a]["f1"])
    print(f"\n  Selected on validation F1: {best}")

    # Retrain on train+val, then touch the test set exactly once.
    Xfull = pd.concat([Xtr, Xva])
    yfull = np.concatenate([ytr, yva])
    final = fit(build_pipeline(Xfull, best), Xfull, yfull, best)

    print("\n" + "=" * 78)
    print(f"FINAL TEST-SET METRICS — {best} (newest 15% of snapshots)")
    print("=" * 78)
    m = evaluate(final, Xte, yte)
    base = evaluate(fit(build_pipeline(Xfull, "majority"), Xfull, yfull, "majority"), Xte, yte)
    print(f"  {'metric':<14}{'model':>10}{'majority':>12}{'delta':>10}")
    print("  " + "-" * 46)
    for k in ("accuracy", "precision", "recall", "f1", "roc_auc", "pr_auc"):
        d = m[k] - base[k]
        d_s = "     n/a" if np.isnan(d) else f"{d:>+10.3f}"
        b_s = "   n/a" if np.isnan(base[k]) else f"{base[k]:>12.3f}"
        print(f"  {k:<14}{m[k]:>10.3f}{b_s}{d_s}")

    pred = final.predict(Xte)
    print("\n  Per-class report")
    print(classification_report(yte, pred, target_names=["resolved", "stale"],
                                zero_division=0, digits=3))
    cm = confusion_matrix(yte, pred)
    print("  Confusion matrix (rows = actual, cols = predicted)")
    print(f"    {'':<12}{'resolved':>10}{'stale':>10}")
    for label, row in zip(["resolved", "stale"], cm):
        print(f"    {label:<12}{row[0]:>10}{row[1]:>10}")

    show_importance(final, best)

    bundle = {
        "pipeline": final,
        "labels": ["resolved", "stale"],
        "feature_columns": feats,
        "algorithm": best,
        "model_version": "1.0.0",
        "prediction_type": "stale_risk",
        "horizon_days": 14,
        "snapshot_definition": "T0 = pr.created_at",
        "test_metrics": m,
    }
    joblib.dump(bundle, args.out)
    print(f"\nSaved model -> {args.out}")
    print(f"  algorithm={best}  horizon=14d  features={len(feats)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
