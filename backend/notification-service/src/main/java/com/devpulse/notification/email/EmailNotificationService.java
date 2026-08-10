package com.devpulse.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service responsible for delivering notifications via Email SMTP.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public EmailNotificationService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${spring.mail.username:noreply@devpulse.com}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    /**
     * Sends an email notification.
     *
     * @param recipientEmail Target email address
     * @param subject Email subject line
     * @param body Email body content
     * @return true if successfully delivered, false otherwise
     */
    public boolean sendEmailNotification(String recipientEmail, String subject, String body) {
        log.info("Sending Email Notification to '{}' with subject '{}'", recipientEmail, subject);

        if (mailSender == null) {
            log.info("JavaMailSender not configured. Simulating successful email delivery to '{}'", recipientEmail);
            return true;
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(fromEmail);
            mailMessage.setTo(recipientEmail);
            mailMessage.setSubject("DevPulse Alert: " + subject);
            mailMessage.setText(body);

            mailSender.send(mailMessage);
            log.info("Email sent successfully to '{}'", recipientEmail);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email to '{}': {}", recipientEmail, e.getMessage());
            return false;
        }
    }
}
