#!/usr/bin/env python3
"""Odin Eye ML — train the stale-PR risk classifier.

Reads pr_dataset.csv (produced by collect_pr_data.py), trains a classifier to
predict the Low / Medium / High staleness bucket, and writes the fitted
pipeline to stale_pr_model.pkl.

Algorithms
----------
The shared database constrains pr_predictions.algorithm to exactly three
values, so those are the three offered here:

    xgboost              gradient-boosted trees (default)
    random_forest        bagged trees
    logistic_regression  linear baseline (numeric features are standardised)

Usage
-----
    python train_model.py                              # xgboost
    python train_model.py --algorithm random_forest
    python train_model.py --data pr_dataset.csv --out stale_pr_model.pkl
    python train_model.py --keep-leaky                 # diagnostic only
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
    from sklearn.ensemble import RandomForestClassifier
    from sklearn.impute import SimpleImputer
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import classification_report, confusion_matrix
    from sklearn.model_selection import cross_val_score, train_test_split
    from sklearn.pipeline import Pipeline
    from sklearn.preprocessing import OneHotEncoder, StandardScaler
    from sklearn.utils.class_weight import compute_sample_weight
except ImportError:
    sys.exit("Missing dependency. Run:  pip install pandas scikit-learn joblib")


TARGET = "staleness_risk"

# Ordered by severity, NOT alphabetically. The index of each label is the
# integer the model is trained on: 0 = Low, 1 = Medium, 2 = High.
LABELS = ["Low", "Medium", "High"]

# Columns that either ARE the label, are derived from it, or are only knowable
# after the PR was resolved. Training on these produces a model that looks
# excellent and is worthless in production.
#
# SAFE vs LEAKY temporal features — the distinction is NOT simply "measured
# from now":
#
#   safe   a value a live PR would genuinely have at scoring time, that was
#          not used to build the label.
#   leaky  anything the label was derived from, or anything only observable
#          once the PR already ended.
#
# time_since_last_activity (now - updated_at)
#   Safe for OPEN PRs: real idle time, exactly what you would compute in
#   production. For CLOSED PRs updated_at freezes at close, so the column
#   silently becomes "how long ago did this PR end" — an artefact of when the
#   snapshot was taken, not a property of the PR.
#
# time_since_created (now - created_at)
#   Safe for CLOSED PRs. For OPEN PRs it is the label: resolution_days is
#   defined as (resolved_at or now) - created_at, which for an open PR is
#   exactly now - created_at. Same arithmetic, same number. Including it lets
#   the model read the answer instead of predicting it, which inflates every
#   score below without improving a single real prediction.
LEAKY = [
    "resolution_days",      # the label is a bucketing of this column
    "state",
    "is_merged",
    "is_open",
    "closed_at",
    "merged_at",
]

# Bookkeeping columns, not signal.
IDENTIFIERS = ["repo", "pr_number", "created_at", "updated_at"]

CATEGORICAL = ["author_association", "base_branch"]

ALGORITHMS = ("xgboost", "random_forest", "logistic_regression")


def load_dataset(path: Path, keep_leaky: bool) -> tuple[pd.DataFrame, pd.Series]:
    if not path.exists():
        sys.exit(f"{path} not found. Run:  python collect_pr_data.py")

    df = pd.read_csv(path)
    print(f"Loaded {len(df)} rows x {len(df.columns)} columns from {path}")

    before = len(df)
    df = df[df[TARGET].isin(LABELS)].copy()
    if len(df) < before:
        print(f"  Dropped {before - len(df)} rows with an Unknown label")

    if len(df) < 50:
        sys.exit(f"Only {len(df)} usable rows — collect more PRs before training.")

    y = df[TARGET]
    drop = IDENTIFIERS + [TARGET] + ([] if keep_leaky else LEAKY)
    X = df.drop(columns=[c for c in drop if c in df.columns])

    if keep_leaky:
        print("  WARNING: --keep-leaky is on. Scores below are optimistic and")
        print("           must not be reported as real performance.")
    else:
        dropped = [c for c in LEAKY if c in df.columns]
        print(f"  Dropped {len(dropped)} leakage-prone columns: {', '.join(dropped)}")

    print(f"  Training on {len(X.columns)} features")
    return X, y


def encode_labels(y: pd.Series) -> tuple[np.ndarray, list[str]]:
    """Map label strings to contiguous ints, ordered by severity.

    XGBoost requires classes numbered 0..k-1 with no gaps. If the dataset is
    missing a class (e.g. no High PRs because the sampling window is too
    short), a fixed Low=0/Medium=1/High=2 mapping would leave a hole and
    XGBoost would refuse to train. So the mapping is built from the classes
    actually present, while keeping severity order.
    """
    present = [label for label in LABELS if label in set(y)]
    mapping = {label: idx for idx, label in enumerate(present)}
    return y.map(mapping).to_numpy(), present


def build_classifier(algorithm: str, n_classes: int):
    if algorithm == "random_forest":
        return RandomForestClassifier(
            n_estimators=300,
            max_depth=12,
            min_samples_leaf=5,
            class_weight="balanced",
            random_state=42,
            n_jobs=-1,
        )

    if algorithm == "logistic_regression":
        return LogisticRegression(
            max_iter=2000,
            class_weight="balanced",
            random_state=42,
        )

    # xgboost — imported lazily so the other two algorithms work without it.
    try:
        from xgboost import XGBClassifier
    except ImportError:
        sys.exit("xgboost is not installed. Run:  pip install xgboost")

    return XGBClassifier(
        n_estimators=400,
        learning_rate=0.05,
        max_depth=6,
        min_child_weight=5,
        subsample=0.8,
        colsample_bytree=0.8,
        reg_lambda=1.0,
        tree_method="hist",
        # Binary and multiclass need different metrics; picking the wrong one
        # makes XGBoost warn and fall back.
        eval_metric="mlogloss" if n_classes > 2 else "logloss",
        random_state=42,
        n_jobs=-1,
    )


def build_pipeline(X: pd.DataFrame, algorithm: str, n_classes: int) -> Pipeline:
    categorical = [c for c in CATEGORICAL if c in X.columns]
    numeric = [c for c in X.columns if c not in categorical]

    # Booleans arrive as True/False; the imputer handles them as numbers.
    for col in numeric:
        if X[col].dtype == bool:
            X[col] = X[col].astype(int)

    # Trees are scale-invariant; logistic regression is not.
    # Annotated because the steps are deliberately heterogeneous: without it a
    # type checker infers list[tuple[str, SimpleImputer]] from the first entry
    # and then rejects appending the StandardScaler below.
    numeric_steps: list[tuple[str, Any]] = [
        ("impute", SimpleImputer(strategy="median"))
    ]
    if algorithm == "logistic_regression":
        numeric_steps.append(("scale", StandardScaler()))

    pre = ColumnTransformer(
        [
            ("num", Pipeline(numeric_steps), numeric),
            (
                "cat",
                OneHotEncoder(handle_unknown="ignore", min_frequency=10),
                categorical,
            ),
        ],
        remainder="drop",
    )

    return Pipeline(
        [("prep", pre), ("clf", build_classifier(algorithm, n_classes))]
    )


def feature_names(pipeline: Pipeline) -> list[str]:
    try:
        return list(pipeline.named_steps["prep"].get_feature_names_out())
    except Exception:
        return []


def report(pipeline: Pipeline, X_test, y_test, classes: list[str]) -> None:
    y_pred = pipeline.predict(X_test)

    print("\n" + "=" * 58)
    print("TEST-SET PERFORMANCE")
    print("=" * 58)

    idx = list(range(len(classes)))
    print(
        classification_report(
            y_test, y_pred, labels=idx, target_names=classes, zero_division=0
        )
    )

    print("Confusion matrix (rows = actual, cols = predicted)")
    cm = confusion_matrix(y_test, y_pred, labels=idx)
    print("           " + "".join(f"{c:>10}" for c in classes))
    for label, row in zip(classes, cm):
        print(f"  {label:<9}" + "".join(f"{v:>10}" for v in row))

    names = feature_names(pipeline)
    clf = pipeline.named_steps["clf"]
    importances = getattr(clf, "feature_importances_", None)
    if importances is None and hasattr(clf, "coef_"):
        # Logistic regression: rank by mean absolute coefficient.
        importances = np.abs(clf.coef_).mean(axis=0)

    if names and importances is not None and len(names) == len(importances):
        total = importances.sum() or 1.0
        print("\nTop 15 features")
        order = np.argsort(importances)[::-1][:15]
        for rank, i in enumerate(order, start=1):
            share = importances[i] / total
            print(f"  {rank:>2}. {names[i]:<38} {share:.4f} {'#' * int(share * 100)}")
    print("=" * 58)


def main() -> int:
    p = argparse.ArgumentParser(description="Train the stale-PR risk model.")
    p.add_argument("--data", default="pr_dataset.csv")
    p.add_argument("--out", default="stale_pr_model.pkl")
    p.add_argument("--algorithm", choices=ALGORITHMS, default="xgboost",
                   help="which model to train (default: xgboost)")
    p.add_argument("--test-size", type=float, default=0.2)
    p.add_argument("--keep-leaky", action="store_true",
                   help="keep outcome-derived columns (diagnostic only)")
    args = p.parse_args()

    print("=" * 58)
    print(f"Odin Eye ML — training the stale-PR classifier ({args.algorithm})")
    print("=" * 58)

    X, y_raw = load_dataset(Path(args.data), args.keep_leaky)
    y, classes = encode_labels(y_raw)

    print("\nClass balance")
    counts = y_raw.value_counts()
    for idx, label in enumerate(classes):
        n = int(counts.get(label, 0))
        print(f"  {idx} = {label:<8} {n:>6}  ({100.0 * n / len(y):5.1f}%)")
    if len(classes) < len(LABELS):
        missing = [c for c in LABELS if c not in classes]
        print(f"  WARNING: no rows for {', '.join(missing)}. The model cannot")
        print("           learn a class it has never seen — collect a wider")
        print("           time window before trusting these results.")

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=args.test_size, random_state=42, stratify=y
    )
    print(f"\nSplit: {len(X_train)} train / {len(X_test)} test")

    pipeline = build_pipeline(X, args.algorithm, len(classes))

    print("Cross-validating (5-fold, macro F1)...")
    scores = cross_val_score(pipeline, X_train, y_train, cv=5, scoring="f1_macro", n_jobs=-1)
    print(f"  macro F1: {scores.mean():.3f} +/- {scores.std():.3f}")

    print("Fitting on the training split...")
    if args.algorithm == "xgboost":
        # XGBoost has no class_weight parameter; balancing is done with
        # per-sample weights instead. (The CV above is unweighted, so a small
        # gap between the two numbers is expected.)
        weights = compute_sample_weight("balanced", y_train)
        pipeline.fit(X_train, y_train, clf__sample_weight=weights)
    else:
        pipeline.fit(X_train, y_train)

    report(pipeline, X_test, y_test, classes)

    bundle = {
        "pipeline": pipeline,
        "labels": classes,           # index position == the integer the model emits
        "feature_columns": list(X.columns),
        "algorithm": args.algorithm,
        "model_version": "0.1.0",
        "prediction_type": "stale_risk",
    }
    joblib.dump(bundle, args.out)
    print(f"\nSaved model -> {args.out}")
    print(f"  algorithm={args.algorithm}  labels={classes}")
    print("  (bundle keys: pipeline, labels, feature_columns, algorithm,")
    print("   model_version, prediction_type — matches the pr_predictions schema)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
