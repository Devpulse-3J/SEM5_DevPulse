package com.devpulse.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** Null-safe readers for the GitHub payload fields that identify a person or a moment in time. */
final class EventJson {

    private EventJson() {
    }

    /**
     * The node's value as an Integer, or null when it is missing, null, not a number
     * or too large for an int.
     *
     * <p>Used for the ids that identify a PERSON (PR author, pusher, Jira assignee).
     * These used to default to 1 via {@code asInt(1)}, and downstream that 1 was
     * looked up as a DevPulse user id - so a payload without the id silently
     * attributed the work to whichever user is number 1 in the company. Jira's
     * assignee id is a string account id, so it was always 1 whenever someone was
     * assigned. Null means "author unknown" and is treated as unattributed.
     */
    static Integer optionalInt(JsonNode node) {
        return node != null && node.isNumber() && node.canConvertToInt() ? node.intValue() : null;
    }

    /**
     * The first of the given nodes that holds a parseable ISO-8601 timestamp, else
     * {@code fallback}. GitHub sends these with an offset (for example
     * {@code 2026-09-25T15:03:36+05:30}) or as UTC ({@code ...Z}).
     *
     * <p>A commit's time must be the commit's own, not the moment we processed the
     * event: stamping "now" put commits imported by the history sync AFTER the
     * deployments that shipped them, which made lead time negative.
     */
    static Instant firstInstant(Instant fallback, JsonNode... candidates) {
        for (JsonNode node : candidates) {
            if (node == null || !node.isTextual()) {
                continue;
            }
            try {
                return OffsetDateTime.parse(node.asText()).toInstant();
            } catch (DateTimeParseException ignored) {
                // try the next candidate
            }
        }
        return fallback;
    }
}
