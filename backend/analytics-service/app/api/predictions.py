"""Prediction routes.

The gateway authenticates at the edge and forwards X-Company-Id; every query
here is scoped by it so one tenant can never score another's pull requests.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, Header, HTTPException, Query
from sqlalchemy.orm import Session

from app.database.session import get_db
from app.ml.feature_extractor import PullRequestNotFound
from app.schemas.prediction import PredictionResponse
from app.services.scoring import score_pull_request

router = APIRouter(prefix="/api/analytics", tags=["predictions"])


def company_id_header(
    x_company_id: str | None = Header(default=None, alias="X-Company-Id"),
) -> int:
    """The gateway sets this from validated JWT claims; empty when absent."""
    if not x_company_id:
        raise HTTPException(status_code=400, detail="X-Company-Id is required")
    try:
        return int(x_company_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="X-Company-Id must be numeric")


@router.post("/predictions/{pr_id}", response_model=PredictionResponse)
def create_prediction(
    pr_id: int,
    company_id: int = Depends(company_id_header),
    include_features: bool = Query(
        default=False, description="Return the feature row used, for debugging"
    ),
    db: Session = Depends(get_db),
) -> PredictionResponse:
    """Score one PR for staleness risk and record it in pr_predictions."""
    try:
        result = score_pull_request(db, pr_id, company_id)
    except PullRequestNotFound:
        raise HTTPException(status_code=404, detail=f"pull request {pr_id} not found")

    if not include_features:
        result.pop("features", None)
    return PredictionResponse(**result)


@router.get("/predictions/{pr_id}/features")
def get_features(
    pr_id: int,
    company_id: int = Depends(company_id_header),
    db: Session = Depends(get_db),
) -> dict:
    """The feature row alone, without scoring or persisting.

    Exists so a training/production feature mismatch can be diagnosed directly
    rather than inferred from a surprising score.
    """
    try:
        result = score_pull_request(db, pr_id, company_id, persist=False)
    except PullRequestNotFound:
        raise HTTPException(status_code=404, detail=f"pull request {pr_id} not found")
    return {"pr_id": pr_id, "features": result["features"]}
