package com.devpulse.notification.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for delivering rich Slack Block Kit alert notifications
 * via Slack Bot API (chat.postMessage) or Incoming Webhooks.
 */
@Service
public class SlackNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SlackNotificationService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String defaultSlackWebhookUrl;
    private final String botToken;

    public SlackNotificationService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${devpulse.notification.slack.webhook-url:}") String defaultSlackWebhookUrl,
            @Value("${devpulse.notification.slack.bot-token:}") String botToken) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.defaultSlackWebhookUrl = defaultSlackWebhookUrl;
        this.botToken = botToken;
    }

    public SlackNotificationService(@Value("${devpulse.notification.slack.webhook-url:}") String defaultSlackWebhookUrl) {
        this(new RestTemplate(), new ObjectMapper(), defaultSlackWebhookUrl, "");
    }

    /**
     * Sends a formatted notification message to a Slack channel or Webhook URL.
     *
     * @param targetChannel Slack channel name/ID or Webhook URL override
     * @param message Text message to deliver
     * @return true if successfully delivered, false otherwise
     */
    public boolean sendSlackNotification(String targetChannel, String message) {
        log.info("Delivering Slack Notification to target '{}': {}", targetChannel, message);

        // 1. Deliver via Incoming Webhook if URL target or default webhook configured
        if (targetChannel != null && targetChannel.startsWith("http")) {
            return postToWebhook(targetChannel, message);
        }

        // 2. Deliver via Slack Bot API (chat.postMessage) if Bot Token configured
        if (botToken != null && !botToken.isBlank() && botToken.startsWith("xoxb-")) {
            return postToSlackBotApi(targetChannel, message);
        }

        // Fallback to default webhook URL if set
        if (defaultSlackWebhookUrl != null && !defaultSlackWebhookUrl.isBlank() && !defaultSlackWebhookUrl.contains("placeholder")) {
            return postToWebhook(defaultSlackWebhookUrl, message);
        }

        // Return false, not true. Reporting success when nothing was sent wrote
        // rows to the `notifications` table with status 'sent' for messages that
        // never left the process — the delivery log claimed a delivery that did
        // not happen, which is worse than a visible failure.
        log.error("Slack is not configured (no bot token and no webhook URL). "
                + "Alert for target '{}' was NOT delivered.", targetChannel);
        return false;
    }

    private boolean postToWebhook(String webhookUrl, String message) {
        try {
            Map<String, Object> slackPayload = buildSlackBlockKitPayload(null, message);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(slackPayload, headers);
            restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("Slack notification posted successfully to Webhook '{}'", webhookUrl);
            return true;
        } catch (Exception e) {
            log.error("Failed to post notification to Slack webhook '{}': {}", webhookUrl, e.getMessage());
            return false;
        }
    }

    private boolean postToSlackBotApi(String channel, String message) {
        String url = "https://slack.com/api/chat.postMessage";
        String channelTarget = (channel != null && !channel.isBlank()) ? channel : "#dev-alerts";

        try {
            Map<String, Object> payload = buildSlackBlockKitPayload(channelTarget, message);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(botToken);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            var response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.path("ok").asBoolean(false)) {
                    log.info("Slack Bot notification posted successfully to channel '{}'", channelTarget);
                    return true;
                } else {
                    log.error("Slack Bot API returned error: {}", root.path("error").asText());
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("Failed to post Slack notification via Bot API to channel '{}': {}", channelTarget, e.getMessage());
        }
        return false;
    }

    private Map<String, Object> buildSlackBlockKitPayload(String targetChannel, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "🚨 *DevPulse Alert*: " + message);
        if (targetChannel != null && !targetChannel.startsWith("http")) {
            payload.put("channel", targetChannel);
        }

        List<Map<String, Object>> blocks = new ArrayList<>();

        // Header Block
        Map<String, Object> headerBlock = new HashMap<>();
        headerBlock.put("type", "header");
        headerBlock.put("text", Map.of("type", "plain_text", "text", "🚨 DevPulse High Risk Alert", "emoji", true));
        blocks.add(headerBlock);

        // Section Block
        Map<String, Object> sectionBlock = new HashMap<>();
        sectionBlock.put("type", "section");
        sectionBlock.put("text", Map.of("type", "mrkdwn", "text", "*Alert Summary:*\n" + message));
        blocks.add(sectionBlock);

        // Context Block
        Map<String, Object> contextBlock = new HashMap<>();
        contextBlock.put("type", "context");
        contextBlock.put("elements", List.of(
                Map.of("type", "mrkdwn", "text", "⚡ *DevPulse Automated Developer Productivity Engine*")
        ));
        blocks.add(contextBlock);

        payload.put("blocks", blocks);
        return payload;
    }
}
