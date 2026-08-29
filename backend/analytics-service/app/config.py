"""Runtime configuration for analytics-service.

Values come from the environment (see .env.local.example). Nothing here reaches
out to the network or the database at import time, so tests can import freely.
"""
from __future__ import annotations

import os
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env.local", extra="ignore")

    # SQLAlchemy DSN. The compose files hand this in already URL-encoded.
    postgres_url: str = "postgresql://localhost:5432/devpulse"
    postgres_user: str | None = None
    postgres_password: str | None = None

    rabbitmq_url: str = "amqp://localhost:5672"

    # Serialised training bundle: {"pipeline", "feature_columns", "horizon_days", ...}
    analytics_model_path: str = "./app/artifacts/stale_pr_model.pkl"

    # Above this probability a PR is worth an alert.pr_high_risk event.
    risk_threshold: float = 0.7

    @property
    def sqlalchemy_url(self) -> str:
        """Splice credentials in only when the DSN does not already carry them."""
        url = self.postgres_url
        if "@" in url or not (self.postgres_user and self.postgres_password):
            return url
        scheme, _, rest = url.partition("://")
        return f"{scheme}://{self.postgres_user}:{self.postgres_password}@{rest}"


@lru_cache
def get_settings() -> Settings:
    return Settings()


# Honour an explicit override in tests without rebuilding the cache.
def reset_settings_cache() -> None:
    get_settings.cache_clear()
    os.environ.pop("_ANALYTICS_SETTINGS_MEMO", None)
