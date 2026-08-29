"""Score a PR and persist the result to pr_predictions.

This is the one place that ties extraction, inference and persistence
together, so both the HTTP route and the RabbitMQ consumer go through it and
cannot drift apart.
"""
from __future__ import annotations

import logging
from datetime import datetime, timezone
from decimal import Decimal

from sqlalchemy.orm import Session

from app.database.models import PrPrediction
from app.ml import feature_extractor
from app.ml.predictor import StalePrModel, load_model

log = logging.getLogger(__name__)


def score_pull_request(
    db: Session,
    pr_id: int,
    company_id: int | None = None,
    *,
    model: StalePrModel | None = None,
    persist: bool = True,
) -> dict:
    """Build features, score, and (by default) write a pr_predictions row.

    Raises feature_extractor.PullRequestNotFound if the PR is not visible to
    this company.
    """
    model = model or load_model()
    pr = feature_extractor.load_pull_request(db, pr_id, company_id)
    features = feature_extractor.features_from_pr(
        db, pr, horizon_days=model.horizon_days
    )
    scored = model.score(features)

    predicted_at = datetime.now(timezone.utc)
    if persist:
        db.add(
            PrPrediction(
                company_id=pr.company_id,
                pr_id=pr.pr_id,
                algorithm=scored["algorithm"],
                model_version=scored["model_version"],
                prediction_type=scored["prediction_type"],
                risk_category=scored["risk_category"],
                risk_score=Decimal(str(scored["risk_score"])),
                predicted_at=predicted_at,
            )
        )
        db.commit()

    return {
        "pr_id": pr.pr_id,
        "company_id": pr.company_id,
        "predicted_at": predicted_at,
        "features": features,
        **scored,
    }
