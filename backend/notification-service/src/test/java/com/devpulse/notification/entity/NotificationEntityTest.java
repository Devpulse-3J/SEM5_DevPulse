package com.devpulse.notification.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationEntityTest {

    @Test
    void testAlertRuleEntity() {
        AlertRule rule = new AlertRule(1, 10, "HIGH_RISK_PR", 24, "#dev-alerts", 5);
        rule.setRuleId(1);

        assertEquals(1, rule.getRuleId());
        assertEquals(1, rule.getCompanyId());
        assertEquals(10, rule.getProjectId());
        assertEquals("HIGH_RISK_PR", rule.getRuleType());
        assertEquals(24, rule.getThresholdHours());
        assertEquals("#dev-alerts", rule.getSlackChannel());
        assertTrue(rule.isActive());
        assertNotNull(rule.getCreatedAt());
    }

    @Test
    void testAlertEntity() {
        Alert alert = new Alert(1, 10, 1, "pull_request", 101, "critical", "High risk PR detected");
        alert.setAlertId(5);

        assertEquals(5, alert.getAlertId());
        assertEquals("pull_request", alert.getEntityType());
        assertEquals(101, alert.getEntityId());
        assertEquals("critical", alert.getSeverity());
        assertEquals("High risk PR detected", alert.getMessage());
        assertNotNull(alert.getTriggeredAt());
    }

    @Test
    void testNotificationEntity() {
        Notification notification = new Notification(1, 5, 20, "slack", "pending");
        notification.setNotificationId(10);

        assertEquals(10, notification.getNotificationId());
        assertEquals(5, notification.getAlertId());
        assertEquals(20, notification.getUserId());
        assertEquals("slack", notification.getChannel());
        assertEquals("pending", notification.getStatus());
    }
}
