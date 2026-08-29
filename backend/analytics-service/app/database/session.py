"""SQLAlchemy engine and session wiring for the shared DevPulse database.

analytics-service READS pull_requests / users / repos and WRITES only
pr_predictions. It never migrates the schema — Flyway in backend/database owns
that (see CLAUDE.md). There is deliberately no Alembic here.

The driver is psycopg2 (sync). Feature extraction is a handful of indexed
queries, so FastAPI's threadpool — which is what a plain `def` endpoint gets —
is a better fit than dragging in an async driver for no measurable win.
"""
from __future__ import annotations

from collections.abc import Iterator

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from app.config import get_settings

_engine = None
_SessionLocal: sessionmaker[Session] | None = None


def get_engine():
    """Create the engine lazily so importing this module never opens a socket."""
    global _engine
    if _engine is None:
        settings = get_settings()
        _engine = create_engine(
            settings.sqlalchemy_url,
            pool_pre_ping=True,  # Supabase's pooler drops idle connections
            pool_size=5,
            max_overflow=5,
            future=True,
        )
    return _engine


def get_session_factory() -> sessionmaker[Session]:
    global _SessionLocal
    if _SessionLocal is None:
        _SessionLocal = sessionmaker(bind=get_engine(), autoflush=False, future=True)
    return _SessionLocal


def get_db() -> Iterator[Session]:
    """FastAPI dependency yielding a session that is always closed."""
    session = get_session_factory()()
    try:
        yield session
    finally:
        session.close()
