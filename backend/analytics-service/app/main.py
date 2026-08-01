"""DevPulse analytics-service — FastAPI app (the ML component).

Scores pull requests for staleness / merge risk. Consumes pr.* events, writes
pr_predictions to the shared database, and publishes alert.pr_high_risk.
"""
from fastapi import FastAPI

app = FastAPI(title="DevPulse Analytics Service", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
