package com.devpulse.notification.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @Test
    void testSendSlackNotificationSimulatedSuccessWhenUnconfigured() {
        SlackNotificationService slackService = new SlackNotificationService(restTemplate, new ObjectMapper(), "", "");
        boolean result = slackService.sendSlackNotification("#dev-alerts", "Test alert message");
        assertTrue(result);
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
}
