"""SQLAlchemy mappings for the tables analytics-service touches.

Only the columns this service actually uses are mapped. These classes mirror a
schema owned by Flyway (backend/database/migrations) — never create or alter
tables from here.

Write access is limited to pr_predictions; PullRequest, User and Repo are
read-only as far as this service is concerned.
"""
from __future__ import annotations

from datetime import datetime
from decimal import Decimal

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    ForeignKey,
    Integer,
    Numeric,
    String,
    Text,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class PullRequest(Base):
    """Owned by metrics-service. Read-only here."""

    __tablename__ = "pull_requests"

    pr_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    company_id: Mapped[int] = mapped_column(Integer, nullable=False)
    repo_id: Mapped[int] = mapped_column(Integer, nullable=False)
    github_pr_id: Mapped[int | None] = mapped_column(BigInteger)
    github_pr_number: Mapped[int] = mapped_column(Integer, nullable=False)
    title: Mapped[str] = mapped_column(String(1024), nullable=False)
    description: Mapped[str | None] = mapped_column(Text)
    author_id: Mapped[int | None] = mapped_column(Integer)
    base_branch: Mapped[str] = mapped_column(String(255), nullable=False)
    is_draft: Mapped[bool] = mapped_column(Boolean, nullable=False)
    state: Mapped[str] = mapped_column(String(20), nullable=False)
    author_association: Mapped[str | None] = mapped_column(String(30))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    merged_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    closed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    @property
    def resolved_at(self) -> datetime | None:
        """Merge wins over close, matching build_snapshots.py."""
        return self.merged_at or self.closed_at


class User(Base):
    """Owned by auth-service. Read-only here."""

    __tablename__ = "users"

    user_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    company_id: Mapped[int] = mapped_column(Integer, nullable=False)
    email: Mapped[str] = mapped_column(String(320), nullable=False)
    full_name: Mapped[str] = mapped_column(String(255), nullable=False)
    github_id: Mapped[int | None] = mapped_column(BigInteger)


class PrPrediction(Base):
    """Owned by analytics-service — the only table it may write."""

    __tablename__ = "pr_predictions"

    prediction_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    company_id: Mapped[int] = mapped_column(Integer, nullable=False)
    pr_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("pull_requests.pr_id"), nullable=False
    )
    algorithm: Mapped[str] = mapped_column(String(50), nullable=False)
    model_version: Mapped[str] = mapped_column(String(20), nullable=False)
    prediction_type: Mapped[str] = mapped_column(String(50), nullable=False)
    risk_category: Mapped[str] = mapped_column(String(20), nullable=False)
    risk_score: Mapped[Decimal] = mapped_column(Numeric(5, 4), nullable=False)
    confidence: Mapped[Decimal | None] = mapped_column(Numeric(5, 4))
    predicted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    actual_outcome: Mapped[str | None] = mapped_column(String(20))
    actual_outcome_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
