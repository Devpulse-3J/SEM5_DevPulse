"""RabbitMQ consumer for pr.* events.

Binds a durable queue to the `devpulse.events` topic exchange, scores each
newly opened PR, and publishes `alert.pr_high_risk` when the score clears
RISK_THRESHOLD. notification-service picks that up — this service does not
know or care who consumes it.

Runs in a background thread beside the FastAPI app (see app/main.py). pika's
BlockingConnection is not thread-safe, so the connection is created inside the
worker thread and never shared.
"""
from __future__ import annotations

import json
import logging
import threading

import pika

from app.config import get_settings
from app.database.session import get_session_factory
from app.ml.feature_extractor import PullRequestNotFound, load_pull_request_by_github_id
from app.services.scoring import score_pull_request

log = logging.getLogger(__name__)

EXCHANGE = "devpulse.events"
QUEUE = "analytics.pr_events"
ROUTING_KEYS = ["pr.opened"]
ALERT_ROUTING_KEY = "alert.pr_high_risk"

# metrics-service writes the PR row from this same event, so it may not be
# there on the first look.
LOOKUP_ATTEMPTS = 4
LOOKUP_DELAY_SECONDS = 2


def _resolve_pr_id(channel, session, github_pr_id: int, company_id: int) -> int | None:
    """Map the event's GitHub PR id to pull_requests.pr_id, waiting briefly."""
    for attempt in range(LOOKUP_ATTEMPTS):
        try:
            return load_pull_request_by_github_id(session, github_pr_id, company_id).pr_id
        except PullRequestNotFound:
            session.rollback()  # end the transaction so the next look sees new rows
            if attempt < LOOKUP_ATTEMPTS - 1:
                # connection.sleep keeps RabbitMQ heartbeats flowing while we wait.
                channel.connection.sleep(LOOKUP_DELAY_SECONDS)
    return None


def _handle_pr_opened(channel, payload: dict) -> None:
    settings = get_settings()
    # `prId` on the event is GitHub's PR id, not pull_requests.pr_id.
    github_pr_id = payload.get("prId")
    company_id = payload.get("companyId")
    if github_pr_id is None or company_id is None:
        log.warning("pr.opened without prId/companyId, dropping: %s", payload.get("eventId"))
        return

    session = get_session_factory()()
    try:
        pr_id = _resolve_pr_id(channel, session, int(github_pr_id), int(company_id))
        if pr_id is None:
            log.warning("pr.opened for unknown github pr id=%s, skipping", github_pr_id)
            return
        result = score_pull_request(session, pr_id, int(company_id))
    finally:
        session.close()

    log.info(
        "Scored pr_id=%s (github id %s) risk=%.4f (%s)",
        pr_id, github_pr_id, result["risk_score"], result["risk_category"],
    )

    if result["risk_score"] >= settings.risk_threshold:
        channel.basic_publish(
            exchange=EXCHANGE,
            routing_key=ALERT_ROUTING_KEY,
            body=json.dumps(
                {
                    "eventType": ALERT_ROUTING_KEY,
                    "companyId": result["company_id"],
                    "prId": result["pr_id"],
                    "riskScore": result["risk_score"],
                    "riskCategory": result["risk_category"],
                    "modelVersion": result["model_version"],
                }
            ).encode(),
            properties=pika.BasicProperties(
                content_type="application/json", delivery_mode=2
            ),
        )


def _on_message(channel, method, _properties, body) -> None:
    try:
        payload = json.loads(body)
    except json.JSONDecodeError:
        log.exception("Undecodable message on %s, discarding", method.routing_key)
        channel.basic_ack(method.delivery_tag)
        return

    try:
        if method.routing_key == "pr.opened":
            _handle_pr_opened(channel, payload)
        channel.basic_ack(method.delivery_tag)
    except Exception:
        # Nack without requeue: a poison message must not block the queue.
        log.exception("Failed handling %s", method.routing_key)
        channel.basic_nack(method.delivery_tag, requeue=False)


def consume_forever() -> None:
    settings = get_settings()
    connection = pika.BlockingConnection(pika.URLParameters(settings.rabbitmq_url))
    channel = connection.channel()
    channel.exchange_declare(EXCHANGE, exchange_type="topic", durable=True)
    channel.queue_declare(QUEUE, durable=True)
    for key in ROUTING_KEYS:
        channel.queue_bind(exchange=EXCHANGE, queue=QUEUE, routing_key=key)

    channel.basic_qos(prefetch_count=10)
    channel.basic_consume(QUEUE, _on_message)
    log.info("Consuming %s from %s", ROUTING_KEYS, QUEUE)
    channel.start_consuming()


def start_background_consumer() -> threading.Thread:
    thread = threading.Thread(target=consume_forever, name="pr-events", daemon=True)
    thread.start()
    return thread
