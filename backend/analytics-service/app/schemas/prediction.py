"""Pydantic response models for the prediction endpoints."""
from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field


class FeatureRow(BaseModel):
    """The exact row handed to the model — useful for debugging drift."""

    title_length: int
    body_length: int
    body_is_empty: int
    base_branch: str | None
    author_association: str | None
    created_hour: int
    created_weekday: int
    created_is_weekend: int
    author_prior_prs: int
    author_prior_stale_rate: float
    author_prior_median_days: float
    author_is_new: int
    repo_recent_stale_rate: float
    repo_recent_median_days: float


class PredictionResponse(BaseModel):
    pr_id: int
    company_id: int
    risk_score: float = Field(ge=0.0, le=1.0)
    risk_category: str
    prediction_type: str
    algorithm: str
    model_version: str
    horizon_days: int
    predicted_at: datetime
    features: FeatureRow | None = None
