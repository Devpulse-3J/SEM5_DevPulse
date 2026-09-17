"""DevPulse analytics-service — FastAPI app (the ML component).

Scores pull requests for staleness / merge risk. Consumes pr.* events, writes
pr_predictions to the shared database, and publishes alert.pr_high_risk.
"""
from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.predictions import router as predictions_router
from app.ml.predictor import load_model

log = logging.getLogger(__name__)

# The consumer needs a broker; leave it off for local API-only work.
ENABLE_CONSUMER = os.getenv("ENABLE_CONSUMER", "true").lower() not in {"0", "false"}


@asynccontextmanager
async def lifespan(_app: FastAPI):
    # Fail fast and loudly: a missing or mismatched bundle should surface at
    # boot, not on the first request.
    try:
        load_model()
    except (FileNotFoundError, ValueError):
        log.exception("Model unavailable — /api/analytics endpoints will fail")

    if ENABLE_CONSUMER:
        try:
            from app.consumers.pr_events import start_background_consumer

            start_background_consumer()
        except Exception:
            log.exception("Could not start the pr.* consumer")

    yield


app = FastAPI(title="DevPulse Analytics Service", version="0.1.0", lifespan=lifespan)
app.include_router(predictions_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
