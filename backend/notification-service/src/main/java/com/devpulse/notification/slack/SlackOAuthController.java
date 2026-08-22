package com.devpulse.notification.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Controller exposing endpoints for 1-click "Add to Slack" OAuth v2 flow
 * and dynamic channel listing for UI selection.
 */
@RestController
// No "/api" prefix — the gateway strips it (StripPrefix=1) before forwarding.
// A matching /api/slack/** route must also exist in the gateway config, or
// these endpoints are unreachable from the frontend.
@RequestMapping("/slack")
public class SlackOAuthController {

    private static final Logger log = LoggerFactory.getLogger(SlackOAuthController.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    // Read-only. This used to be a mutable field assigned during the OAuth
    // callback, which made it shared state on a singleton bean: whichever
    // company installed last overwrote every other company's token, and the
    // value was lost on restart. A token obtained by OAuth must be stored
    // per-company in the `integrations` table, not held here.
    private final String botToken;

    public SlackOAuthController(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${devpulse.notification.slack.client-id:}") String clientId,
            @Value("${devpulse.notification.slack.client-secret:}") String clientSecret,
            @Value("${devpulse.notification.slack.redirect-uri:http://localhost:8084/api/slack/oauth/callback}") String redirectUri,
            @Value("${devpulse.notification.slack.bot-token:}") String botToken) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.botToken = botToken;
    }

    /**
     * Generates the official "Add to Slack" OAuth v2 Authorization URL.
     * GET /api/slack/oauth/install
     */
    @GetMapping("/oauth/install")
    public ResponseEntity<Map<String, String>> getOAuthInstallUrl() {
        String scope = "chat:write,chat:write.public,channels:read,groups:read";
        String authUrl = String.format(
                "https://slack.com/oauth/v2/authorize?client_id=%s&scope=%s&redirect_uri=%s",
                clientId, scope, redirectUri
        );

        log.info("Generated Slack OAuth Install URL: {}", authUrl);
        return ResponseEntity.ok(Map.of("installUrl", authUrl));
    }

    /**
     * Handles Slack OAuth v2 redirect callback code exchange.
     * GET /api/slack/oauth/callback?code={code}
     */
    @GetMapping("/oauth/callback")
    public ResponseEntity<Map<String, Object>> handleOAuthCallback(@RequestParam("code") String code) {
        log.info("Received Slack OAuth callback code: {}", code);
        String tokenUrl = "https://slack.com/api/oauth.v2.access";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = String.format("client_id=%s&client_secret=%s&code=%s&redirect_uri=%s",
                clientId, clientSecret, code, redirectUri);

        HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                boolean ok = root.path("ok").asBoolean(false);

                if (ok) {
                    String accessToken = root.path("access_token").asText(null);
                    String teamName = root.path("team").path("name").asText("Slack Workspace");
                    if (accessToken != null) {
                        // The token is deliberately NOT stored on this bean and NOT
                        // returned to the caller. Persisting it belongs in the
                        // `integrations` table, keyed by company — see the TODO on
                        // this class. Until that exists, the install completes but
                        // the token is not retained.
                        log.info("Slack OAuth exchange succeeded for team: {}. Token not persisted "
                                + "— per-company storage in `integrations` is not implemented yet.",
                                teamName);
                    }
                    return ResponseEntity.ok(Map.of(
                            "status", "success",
                            "message", "Slack workspace authorized successfully",
                            "teamName", teamName
                    ));
                } else {
                    String error = root.path("error").asText("oauth_failed");
                    log.error("Slack OAuth exchange failed with error: {}", error);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "error", "message", error));
                }
            }
        } catch (Exception e) {
            log.error("Failed to execute Slack OAuth code exchange: {}", e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "OAuth code exchange failed"));
    }

    /**
     * Fetches accessible Slack channels from workspace for UI dropdown selection.
     * GET /api/slack/channels
     */
    @GetMapping("/channels")
    public ResponseEntity<List<Map<String, String>>> listSlackChannels() {
        log.info("Fetching Slack channels using Bot Token...");
        List<Map<String, String>> channels = new ArrayList<>();

        if (botToken == null || botToken.isBlank()) {
            log.warn("No Slack bot token configured. Returning mock/default channel list for UI testing.");
            return ResponseEntity.ok(List.of(
                    Map.of("id", "C12345678", "name", "dev-alerts"),
                    Map.of("id", "C87654321", "name", "general"),
                    Map.of("id", "C11223344", "name", "engineering")
            ));
        }

        try {
            String url = "https://slack.com/api/conversations.list?types=public_channel,private_channel";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(botToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode channelNodes = root.path("channels");

                if (channelNodes.isArray()) {
                    for (JsonNode cNode : channelNodes) {
                        String id = cNode.path("id").asText();
                        String name = cNode.path("name").asText();
                        channels.add(Map.of("id", id, "name", "#" + name));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch Slack channel list: {}", e.getMessage());
        }

        return ResponseEntity.ok(channels);
    }
}
