package com.devpulse.notification.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailNotificationServiceTest {

    @Test
    void testSendEmailNotificationSimulatedSuccess() {
        EmailNotificationService emailService = new EmailNotificationService(null, "noreply@devpulse.com");
        boolean result = emailService.sendEmailNotification("user@example.com", "High Risk Alert", "Alert body details");
        assertTrue(result);
    }
}
