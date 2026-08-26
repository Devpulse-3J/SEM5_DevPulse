package com.devpulse.notification.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SlackOAuthControllerTest {

    private RestTemplate restTemplate;
    private SlackOAuthController controller;

    @BeforeEach
    public void setUp() {
        restTemplate = mock(RestTemplate.class);
        controller = new SlackOAuthController(
                restTemplate,
                new ObjectMapper(),
                "test-client-id",
                "test-client-secret",
                "http://localhost:8084/api/slack/oauth/callback",
                ""
        );
    }

    @Test
    public void testGetOAuthInstallUrl() {
        ResponseEntity<Map<String, String>> response = controller.getOAuthInstallUrl();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().get("installUrl").contains("slack.com/oauth/v2/authorize"));
        assertTrue(response.getBody().get("installUrl").contains("test-client-id"));
    }

    @Test
    public void testHandleOAuthCallbackSuccess() {
        String successJson = "{\"ok\": true, \"access_token\": \"xoxb-test-token\", \"team\": {\"name\": \"DevPulse Team\"}}";
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(successJson));

        ResponseEntity<Map<String, Object>> response = controller.handleOAuthCallback("test-code");
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("success", response.getBody().get("status"));
        assertEquals("DevPulse Team", response.getBody().get("teamName"));
    }

    @Test
    public void testListSlackChannelsFallback() {
        ResponseEntity<List<Map<String, String>>> response = controller.listSlackChannels();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertFalse(response.getBody().isEmpty());
        assertEquals("dev-alerts", response.getBody().get(0).get("name"));
    }
}
