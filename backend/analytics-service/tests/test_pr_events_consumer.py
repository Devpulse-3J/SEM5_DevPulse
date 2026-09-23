"""pr.opened events carry GitHub's PR id in `prId`, not pull_requests.pr_id.

Regression: the consumer used to look `prId` up as pull_requests.pr_id, so
every event was skipped as "unknown pr_id" and nothing was ever scored. These
tests run against in-memory SQLite with a fake channel and a fake scorer.
"""
from __future__ import annotations

import sys
from datetime import datetime, timezone
from pathlib import Path

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.consumers import pr_events  # noqa: E402
from app.database.models import Base, PullRequest  # noqa: E402
from app.ml.feature_extractor import (  # noqa: E402
    PullRequestNotFound,
    load_pull_request_by_github_id,
)

COMPANY = 1
# Real events carry ids like this: GitHub's id wrapped into a 32-bit int.
GITHUB_ID = -126120538


@pytest.fixture
def factory():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    return sessionmaker(engine, expire_on_commit=False)


def _add_pr(factory, pr_id: int, github_pr_id: int, company_id: int = COMPANY) -> None:
    with factory() as session:
        session.add(
            PullRequest(
                pr_id=pr_id,
                company_id=company_id,
                repo_id=1,
                github_pr_id=github_pr_id,
                github_pr_number=pr_id,
                title="t",
                description="d",
                author_id=1,
                base_branch="master",
                is_draft=False,
                state="open",
                created_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
            )
        )
        session.commit()


class FakeChannel:
    def __init__(self, on_sleep=None):
        self.sleeps: list[float] = []
        self.published: list[dict] = []
        self._on_sleep = on_sleep
        self.connection = self

    def sleep(self, seconds: float) -> None:
        self.sleeps.append(seconds)
        if self._on_sleep:
            self._on_sleep(len(self.sleeps))

    def basic_publish(self, **kwargs) -> None:
        self.published.append(kwargs)


@pytest.fixture
def scored(monkeypatch, factory):
    """Patch the session factory and the scorer; record what gets scored."""
    calls: list[tuple[int, int]] = []

    def fake_score(session, pr_id, company_id=None, **_):
        calls.append((pr_id, company_id))
        return {
            "pr_id": pr_id,
            "company_id": company_id,
            "risk_score": 0.9,
            "risk_category": "high",
            "model_version": "1.0.0",
        }

    monkeypatch.setattr(pr_events, "get_session_factory", lambda: factory)
    monkeypatch.setattr(pr_events, "score_pull_request", fake_score)
    return calls


def _event(**overrides) -> dict:
    payload = {"eventId": "e1", "prId": GITHUB_ID, "companyId": COMPANY}
    payload.update(overrides)
    return payload


def test_lookup_by_github_id_returns_the_row(factory):
    _add_pr(factory, pr_id=7, github_pr_id=GITHUB_ID)
    with factory() as session:
        assert load_pull_request_by_github_id(session, GITHUB_ID, COMPANY).pr_id == 7


def test_lookup_is_scoped_to_the_company(factory):
    _add_pr(factory, pr_id=7, github_pr_id=GITHUB_ID, company_id=2)
    with factory() as session:
        with pytest.raises(PullRequestNotFound):
            load_pull_request_by_github_id(session, GITHUB_ID, COMPANY)


def test_event_scores_the_database_pr_id_not_the_github_id(factory, scored):
    _add_pr(factory, pr_id=7, github_pr_id=GITHUB_ID)
    channel = FakeChannel()

    pr_events._handle_pr_opened(channel, _event())

    assert scored == [(7, COMPANY)]
    assert channel.sleeps == []


def test_high_risk_publishes_an_alert_with_the_database_pr_id(factory, scored):
    _add_pr(factory, pr_id=7, github_pr_id=GITHUB_ID)
    channel = FakeChannel()

    pr_events._handle_pr_opened(channel, _event())

    assert len(channel.published) == 1
    assert channel.published[0]["routing_key"] == "alert.pr_high_risk"
    assert b'"prId": 7' in channel.published[0]["body"]


def test_waits_for_metrics_service_to_insert_the_pr(factory, scored):
    channel = FakeChannel(
        on_sleep=lambda n: _add_pr(factory, pr_id=7, github_pr_id=GITHUB_ID) if n == 1 else None
    )

    pr_events._handle_pr_opened(channel, _event())

    assert scored == [(7, COMPANY)]
    assert len(channel.sleeps) == 1


def test_gives_up_quietly_when_the_pr_never_appears(factory, scored):
    channel = FakeChannel()

    pr_events._handle_pr_opened(channel, _event())

    assert scored == []
    assert len(channel.sleeps) == pr_events.LOOKUP_ATTEMPTS - 1
    assert channel.published == []


@pytest.mark.parametrize("missing", ["prId", "companyId"])
def test_event_without_prid_or_companyid_is_dropped_without_a_lookup(factory, scored, missing):
    channel = FakeChannel()
    payload = _event()
    del payload[missing]

    pr_events._handle_pr_opened(channel, payload)

    assert scored == []
    assert channel.sleeps == []


# -- consume_forever: must never give up on a transient connection failure ---
#
# Regression: this used to be a single unguarded pika.BlockingConnection(...)
# call. Any failure - most commonly RabbitMQ not being reachable yet the
# instant this background thread starts - raised out of the thread's target
# function and killed it silently for the rest of the process's life. 232
# pr.opened events backed up with zero consumers before this was caught.

class _StopLoop(BaseException):
    """Escapes the infinite retry loop after the behaviour under test is observed.

    Must NOT subclass Exception: consume_forever()'s own `except Exception:`
    would otherwise catch it as just another connection failure and retry
    forever instead of letting it propagate - which is exactly what happened
    here once, running the fake connection function in an unbounded loop.
    """


def test_a_connection_failure_at_startup_is_retried_not_fatal(monkeypatch):
    attempts: list[int] = []
    sleeps: list[float] = []

    def fake_connect():
        attempts.append(1)
        if len(attempts) < 3:
            raise pika.exceptions.AMQPConnectionError("not ready yet")
        raise _StopLoop  # stand in for a clean run once connected

    monkeypatch.setattr(pr_events, "_connect_and_consume", fake_connect)
    monkeypatch.setattr(pr_events.time, "sleep", lambda s: sleeps.append(s))

    with pytest.raises(_StopLoop):
        pr_events.consume_forever()

    assert len(attempts) == 3, "must keep retrying past the first (and second) failure"


def test_retry_delay_backs_off_and_is_capped(monkeypatch):
    sleeps: list[float] = []
    calls = {"n": 0}

    def fake_connect():
        calls["n"] += 1
        if calls["n"] > 5:
            raise _StopLoop
        raise pika.exceptions.AMQPConnectionError("still down")

    monkeypatch.setattr(pr_events, "_connect_and_consume", fake_connect)
    monkeypatch.setattr(pr_events.time, "sleep", lambda s: sleeps.append(s))

    with pytest.raises(_StopLoop):
        pr_events.consume_forever()

    assert sleeps[0] == pr_events.RECONNECT_DELAY_SECONDS
    assert sleeps == sorted(sleeps), "delay must not decrease between retries"
    assert all(s <= pr_events.MAX_RECONNECT_DELAY_SECONDS for s in sleeps), "must stay capped"


def test_a_mid_run_disconnect_is_also_retried(monkeypatch):
    """A drop after connecting successfully (e.g. StreamLostError from inside
    start_consuming()) must be retried exactly like a startup failure - the
    old code had no protection here either, since everything lived in one
    unguarded call.
    """
    calls = {"n": 0}

    def fake_connect():
        calls["n"] += 1
        if calls["n"] == 1:
            return None  # first "connection" runs cleanly...
        if calls["n"] == 2:
            raise pika.exceptions.StreamLostError("broker went away mid-run")
        raise _StopLoop

    monkeypatch.setattr(pr_events, "_connect_and_consume", fake_connect)
    monkeypatch.setattr(pr_events.time, "sleep", lambda s: None)

    with pytest.raises(_StopLoop):
        pr_events.consume_forever()

    assert calls["n"] == 3
