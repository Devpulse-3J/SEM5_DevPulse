package com.devpulse.notification.webhook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookNotificationServiceTest {

    @Test
    void testSendWebhookNotificationSimulation() {
        WebhookNotificationService service = new WebhookNotificationService("");
        boolean result = service.sendWebhookNotification(null, "Test Payload");
        assertTrue(result);
    }
}
