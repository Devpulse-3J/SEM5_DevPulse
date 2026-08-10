package com.devpulse.notification.slack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlackNotificationServiceTest {

    @Test
    void testSendSlackNotificationSimulatedSuccess() {
        SlackNotificationService slackService = new SlackNotificationService(null);
        boolean result = slackService.sendSlackNotification("#dev-alerts", "Test alert message");
        assertTrue(result);
    }
}
