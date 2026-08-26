package com.devpulse.notification.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service responsible for delivering notifications via Webhooks.
 */
@Service
public class WebhookNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationService.class);

    private final RestTemplate restTemplate;
    private final String defaultWebhookUrl;

    public WebhookNotificationService(
            @Value("${devpulse.notification.webhook.url:}") String defaultWebhookUrl) {
        this.restTemplate = new RestTemplate();
        this.defaultWebhookUrl = defaultWebhookUrl;
    }

    /**
     * Sends a webhook notification payload to a target URL or default configured webhook URL.
     *
     * @param targetWebhookUrl Webhook URL override or target endpoint
     * @param payload Payload object or map to deliver
     * @return true if successfully delivered, false otherwise
     */
    public boolean sendWebhookNotification(String targetWebhookUrl, Object payload) {
        String webhookUrl = (targetWebhookUrl != null && !targetWebhookUrl.isBlank())
                ? targetWebhookUrl
                : defaultWebhookUrl;

        log.info("Delivering Webhook Notification to url '{}'", targetWebhookUrl);

        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("Webhook URL not configured. Simulating successful webhook delivery to '{}'", targetWebhookUrl);
            return true;
        }

        try {
            restTemplate.postForEntity(webhookUrl, payload, String.class);
            log.info("Webhook notification posted successfully to '{}'", webhookUrl);
            return true;
        } catch (Exception e) {
            log.error("Failed to post notification to webhook '{}': {}", webhookUrl, e.getMessage());
            return false;
        }
    }
}
