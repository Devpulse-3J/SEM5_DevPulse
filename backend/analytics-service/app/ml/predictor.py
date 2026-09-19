"""Load the trained bundle and turn a feature row into a scored prediction.

The bundle is what train_snapshot_model.py wrote:

    {"pipeline", "feature_columns", "algorithm", "model_version",
     "prediction_type", "horizon_days", "labels", "test_metrics"}

Because the pipeline carries its own imputer/encoder, the only contract this
module must honour is column ORDER and NAMES — hence the assertion against
`feature_columns` on load.
"""
from __future__ import annotations

import logging
import threading
from pathlib import Path
from typing import Any

import joblib
import pandas as pd

from app.config import get_settings
from app.ml.feature_extractor import DEFAULT_HORIZON_DAYS, FEATURE_COLUMNS

log = logging.getLogger(__name__)

# Bucket edges mirror predict.py, and the three values pr_predictions accepts.
LOW_MAX, MEDIUM_MAX = 0.34, 0.67


def risk_category(probability: float) -> str:
    if probability <= LOW_MAX:
        return "low"
    if probability <= MEDIUM_MAX:
        return "medium"
    return "high"


class StalePrModel:
    """A loaded model bundle. Immutable once constructed."""

    def __init__(self, bundle: dict[str, Any]) -> None:
        self.pipeline = bundle["pipeline"]
        self.feature_columns: list[str] = list(bundle["feature_columns"])
        self.algorithm: str = bundle["algorithm"]
        self.model_version: str = bundle["model_version"]
        self.prediction_type: str = bundle.get("prediction_type", "stale_risk")
        self.horizon_days: int = int(bundle.get("horizon_days", DEFAULT_HORIZON_DAYS))

        unexpected = set(self.feature_columns) ^ set(FEATURE_COLUMNS)
        if unexpected:
            raise ValueError(
                "Model bundle disagrees with the feature extractor on columns: "
                f"{sorted(unexpected)}. Retrain, or update FEATURE_COLUMNS."
            )

    def predict_proba(self, features: dict) -> float:
        """P(this PR is still unresolved horizon_days after it opened)."""
        frame = pd.DataFrame([features])[self.feature_columns]
        return float(self.pipeline.predict_proba(frame)[:, 1][0])

    def score(self, features: dict) -> dict:
        probability = self.predict_proba(features)
        return {
            "risk_score": round(probability, 4),
            "risk_category": risk_category(probability),
            "algorithm": self.algorithm,
            "model_version": self.model_version,
            "prediction_type": self.prediction_type,
            "horizon_days": self.horizon_days,
        }


_model: StalePrModel | None = None
_lock = threading.Lock()


def load_model(path: str | Path | None = None) -> StalePrModel:
    """Load and cache the bundle. Safe to call from several worker threads."""
    global _model
    if _model is not None and path is None:
        return _model

    resolved = Path(path or get_settings().analytics_model_path)
    if not resolved.exists():
        raise FileNotFoundError(
            f"Model artifact not found at {resolved}. Copy the trained bundle "
            "into place, or point ANALYTICS_MODEL_PATH at it "
            "(training/stale_pr_model.pkl)."
        )

    with _lock:
        model = StalePrModel(joblib.load(resolved))
        if path is None:
            _model = model
        log.info(
            "Loaded %s v%s (horizon %sd) from %s",
            model.algorithm, model.model_version, model.horizon_days, resolved,
        )
        return model


def reset_model_cache() -> None:
    global _model
    _model = None
