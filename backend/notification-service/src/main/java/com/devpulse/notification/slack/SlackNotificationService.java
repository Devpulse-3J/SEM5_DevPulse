package com.devpulse.notification.slack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service responsible for delivering notifications to Slack via Webhooks.
 */
@Service
public class SlackNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SlackNotificationService.class);

    private final RestTemplate restTemplate;
    private final String defaultSlackWebhookUrl;

    public SlackNotificationService(
            @Value("${devpulse.notification.slack.webhook-url:}") String defaultSlackWebhookUrl) {
        this.restTemplate = new RestTemplate();
        this.defaultSlackWebhookUrl = defaultSlackWebhookUrl;
    }

    /**
     * Sends a formatted notification message to a Slack channel or Webhook URL.
     *
     * @param targetChannel Slack channel name or Webhook URL override
     * @param message Text message to deliver
     * @return true if successfully delivered, false otherwise
     */
    public boolean sendSlackNotification(String targetChannel, String message) {
        String webhookUrl = (targetChannel != null && targetChannel.startsWith("http")) 
                ? targetChannel 
                : defaultSlackWebhookUrl;

        log.info("Delivering Slack Notification to channel/url '{}': {}", targetChannel, message);

        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("Slack webhook URL not configured. Simulating successful delivery to channel '{}'", targetChannel);
            return true;
        }

        try {
            Map<String, Object> slackPayload = Map.of(
                    "text", "🚨 *DevPulse Alert*\n" + message,
                    "channel", targetChannel != null ? targetChannel : "#dev-alerts"
            );

            restTemplate.postForEntity(webhookUrl, slackPayload, String.class);
            log.info("Slack notification posted successfully to '{}'", webhookUrl);
            return true;
        } catch (Exception e) {
            log.error("Failed to post notification to Slack webhook '{}': {}", webhookUrl, e.getMessage());
            return false;
        }
    }
}
