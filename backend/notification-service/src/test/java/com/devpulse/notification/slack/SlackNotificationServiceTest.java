package com.devpulse.notification.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SlackNotificationServiceTest {

    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
    }

    /**
     * With no bot token and no webhook URL, delivery must report failure.
     *
     * <p>This previously asserted {@code true} — the service logged
     * "Simulating successful delivery" and returned success. That caused every
     * row in the {@code notifications} table to be written with status 'sent'
     * for a message that never left the process, so the delivery log recorded
     * deliveries that never happened.
     */
    @Test
    void testSendSlackNotificationFailsWhenUnconfigured() {
        SlackNotificationService slackService = new SlackNotificationService(restTemplate, new ObjectMapper(), "", "");
        boolean result = slackService.sendSlackNotification("#dev-alerts", "Test alert message");
        assertFalse(result);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void testSendSlackNotificationHttpSuccessWithUrl() {
        SlackNotificationService slackService = new SlackNotificationService(restTemplate, new ObjectMapper(), "https://hooks.slack.com/services/test", "");
        when(restTemplate.postForEntity(eq("https://hooks.slack.com/services/test"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        boolean result = slackService.sendSlackNotification(null, "Test alert message");
        assertTrue(result);
        verify(restTemplate).postForEntity(eq("https://hooks.slack.com/services/test"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void testSendSlackNotificationBotApiSuccess() {
        SlackNotificationService slackService = new SlackNotificationService(restTemplate, new ObjectMapper(), "", "xoxb-test-bot-token");
        String botResponseJson = "{\"ok\": true, \"ts\": \"1234567890.123456\"}";

        when(restTemplate.postForEntity(eq("https://slack.com/api/chat.postMessage"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(botResponseJson));

        boolean result = slackService.sendSlackNotification("#dev-alerts", "Bot alert message");
        assertTrue(result);
        verify(restTemplate).postForEntity(eq("https://slack.com/api/chat.postMessage"), any(HttpEntity.class), eq(String.class));
    }

    /**
     * This service and SlackOAuthController read {@code devpulse.notification.slack.*},
     * while the deployment supplies plain {@code SLACK_*} environment variables. Only
     * application.yml connects the two. When that block was missing every send was
     * reported as "Slack is not configured" and the team-message endpoint answered 502.
     */
    @Test
    void everySlackPropertyTheCodeReadsIsBoundToItsEnvironmentVariable() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();

        assertEquals("${SLACK_WEBHOOK_URL:}", props.getProperty("devpulse.notification.slack.webhook-url"));
        assertEquals("${SLACK_BOT_TOKEN:}", props.getProperty("devpulse.notification.slack.bot-token"));
        assertEquals("${SLACK_CLIENT_ID:}", props.getProperty("devpulse.notification.slack.client-id"));
        assertEquals("${SLACK_CLIENT_SECRET:}", props.getProperty("devpulse.notification.slack.client-secret"));
    }
}
