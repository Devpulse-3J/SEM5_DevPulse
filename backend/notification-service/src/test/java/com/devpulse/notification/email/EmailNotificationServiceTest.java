package com.devpulse.notification.email;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailNotificationServiceTest {

    @Test
    void reportsNotSentWhenMailIsNotConfigured() {
        // Reporting success here would record a notification as "sent" that nobody received.
        EmailNotificationService emailService = new EmailNotificationService(null, "noreply@devpulse.com");
        assertFalse(emailService.sendEmailNotification("user@example.com", "High Risk Alert", "Alert body details"));
    }

    @Test
    void sendsThroughTheConfiguredMailSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailNotificationService emailService = new EmailNotificationService(mailSender, "noreply@devpulse.com");

        assertTrue(emailService.sendEmailNotification("manager@example.com", "Deployment failed", "body"));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void reportsNotSentWhenTheMailServerRejectsIt() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("auth failed")).when(mailSender).send(any(SimpleMailMessage.class));
        EmailNotificationService emailService = new EmailNotificationService(mailSender, "noreply@devpulse.com");

        assertFalse(emailService.sendEmailNotification("manager@example.com", "Deployment failed", "body"));
    }
}
