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
from app.ml.feature_extractor import PullRequestNotFound
from app.services.scoring import score_pull_request

log = logging.getLogger(__name__)

EXCHANGE = "devpulse.events"
QUEUE = "analytics.pr_events"
ROUTING_KEYS = ["pr.opened"]
ALERT_ROUTING_KEY = "alert.pr_high_risk"


def _handle_pr_opened(channel, payload: dict) -> None:
    settings = get_settings()
    pr_id = payload.get("prId")
    company_id = payload.get("companyId")
    if pr_id is None:
        log.warning("pr.opened without prId, dropping: %s", payload.get("eventId"))
        return

    session = get_session_factory()()
    try:
        result = score_pull_request(session, int(pr_id), company_id)
    except PullRequestNotFound:
        # metrics-service may not have persisted the PR yet. Requeueing would
        # spin; the PR is scored on the next event or by an explicit call.
        log.warning("pr.opened for unknown pr_id=%s, skipping", pr_id)
        return
    finally:
        session.close()

    log.info(
        "Scored pr_id=%s risk=%.4f (%s)",
        pr_id, result["risk_score"], result["risk_category"],
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
