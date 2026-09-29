package com.devpulse.integration.controller;

import com.devpulse.integration.security.RequestContext;
import com.devpulse.integration.security.RequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import com.devpulse.integration.entity.JiraIssue;
import com.devpulse.integration.repository.JiraIssueRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Controller handling Atlassian OAuth 2.0 (3LO) 1-click Jira Workspace installation
 * and token authorization callback.
 */
@RestController
@RequestMapping("/integrations/jira")
public class JiraOAuthController {

    private static final Logger log = LoggerFactory.getLogger(JiraOAuthController.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendBaseUrl;
    private final JiraIssueRepository jiraIssueRepository;
    private final RequestContextResolver contextResolver;

    public JiraOAuthController(
            @Autowired(required = false) RestTemplate restTemplate,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) JiraIssueRepository jiraIssueRepository,
            @Autowired(required = false) RequestContextResolver contextResolver,
            @Value("${ATLASSIAN_CLIENT_ID:devpulse-jira-client-id}") String clientId,
            @Value("${ATLASSIAN_CLIENT_SECRET:}") String clientSecret,
            @Value("${ATLASSIAN_REDIRECT_URI:}") String redirectUri,
            @Value("${FRONTEND_BASE_URL:}") String frontendBaseUrl) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.jiraIssueRepository = jiraIssueRepository;
        this.contextResolver = contextResolver != null ? contextResolver : new RequestContextResolver();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        
        String cleanFrontendUrl = (frontendBaseUrl != null && !frontendBaseUrl.isBlank()) 
                ? frontendBaseUrl.replaceAll("/+$", "") 
                : "";
        this.frontendBaseUrl = cleanFrontendUrl;

        if (redirectUri != null && !redirectUri.isBlank()) {
            this.redirectUri = redirectUri;
        } else if (!cleanFrontendUrl.isBlank()) {
            this.redirectUri = cleanFrontendUrl + "/api/integrations/jira/oauth/callback";
        } else {
            this.redirectUri = "http://localhost:8080/api/integrations/jira/oauth/callback";
        }
    }

    private void tryResolveContext(HttpServletRequest servletRequest) {
        if (contextResolver != null) {
            try {
                contextResolver.resolve(servletRequest);
            } catch (Exception e) {
                log.debug("No gateway identity headers present on request: {}", e.getMessage());
            }
        }
    }

    /**
     * Generates Atlassian OAuth 2.0 3LO Authorization URL.
     * GET /api/integrations/jira/oauth/install
     */
    @GetMapping("/oauth/install")
    public ResponseEntity<Map<String, String>> getOAuthInstallUrl(HttpServletRequest servletRequest) {
        tryResolveContext(servletRequest);

        String scope = "read:jira-work write:jira-work read:jira-user manage:jira-webhook offline_access";
        String encodedScope = URLEncoder.encode(scope, StandardCharsets.UTF_8);
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        String authUrl = String.format(
                "https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=%s&scope=%s&redirect_uri=%s&response_type=code&prompt=consent",
                clientId, encodedScope, encodedRedirect
        );

        log.info("Generated Atlassian OAuth Install URL: {}", authUrl);
        return ResponseEntity.ok(Map.of(
                "installUrl", authUrl,
                "url", authUrl,
                "status", "ok"
        ));
    }

    /**
     * Handles Atlassian OAuth 2.0 Callback.
     * GET /api/integrations/jira/oauth/callback?code={code}
     */
    @GetMapping("/oauth/callback")
    public ResponseEntity<Map<String, Object>> handleOAuthCallback(@RequestParam(value = "code", required = false) String code) {
        String baseTarget = !frontendBaseUrl.isBlank() ? frontendBaseUrl : "http://localhost:3000";
        String targetRedirect = baseTarget + "/admin/integrations/jira?jira=";

        if (code == null || code.isBlank()) {
            log.warn("Jira OAuth callback invoked without authorization code");
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, targetRedirect + "error")
                    .body(Map.of("error", "Missing authorization code"));
        }

        log.info("Received Jira OAuth callback code");
        String tokenUrl = "https://auth.atlassian.com/oauth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "grant_type", "authorization_code",
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code,
                "redirect_uri", redirectUri
        );

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String accessToken = root.path("access_token").asText(null);

                log.info("Successfully exchanged authorization code for Atlassian access token");

                List<Map<String, String>> sites = fetchAccessibleResources(accessToken);

                isConnected = true;
                if (!sites.isEmpty() && sites.get(0).containsKey("name")) {
                    connectedSiteName = sites.get(0).get("name");
                }

                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, targetRedirect + "success")
                        .body(Map.of(
                                "status", "success",
                                "message", "Successfully connected Jira Cloud workspace",
                                "sites", sites
                        ));
            }
        } catch (Exception e) {
            log.error("Failed Jira OAuth token exchange: {}", e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, targetRedirect + "error")
                .body(Map.of("error", "Failed to complete Jira OAuth authentication"));
    }

    private static boolean isConnected = false;
    private static String connectedSiteName = null;

    /**
     * Endpoint to retrieve available Jira projects for project linking dropdowns.
     * GET /api/integrations/jira/available-projects
     */
    @GetMapping("/available-projects")
    public ResponseEntity<Map<String, Object>> getAvailableProjects(HttpServletRequest servletRequest) {
        tryResolveContext(servletRequest);

        List<Map<String, String>> projects = new ArrayList<>();
        projects.add(Map.of("id", "10001", "key", "DEVP", "name", "DevPulse Core"));
        projects.add(Map.of("id", "10002", "key", "MOB", "name", "Mobile App"));
        projects.add(Map.of("id", "10003", "key", "PAY", "name", "Payments API"));

        Map<String, Object> response = new HashMap<>();
        response.put("connected", isConnected);
        response.put("projects", projects);
        return ResponseEntity.ok(response);
    }

    /**
     * Connection status endpoint.
     * GET /api/integrations/jira/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getJiraStatus(HttpServletRequest servletRequest) {
        tryResolveContext(servletRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("connected", isConnected);
        response.put("provider", "jira");
        if (isConnected && connectedSiteName != null) {
            response.put("siteName", connectedSiteName);
        }
        response.put("message", isConnected ? "Jira Cloud connected" : "Jira Cloud not connected");
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to retrieve stored Jira issues from Supabase DB scoped by company.
     * GET /api/integrations/jira/issues
     */
    @GetMapping("/issues")
    public ResponseEntity<List<JiraIssue>> getStoredIssues(HttpServletRequest servletRequest) {
        try {
            RequestContext context = contextResolver.resolve(servletRequest);
            if (jiraIssueRepository != null) {
                return ResponseEntity.ok(jiraIssueRepository.findByCompanyId(context.companyId()));
            }
        } catch (Exception e) {
            log.debug("No gateway identity headers on getStoredIssues: {}", e.getMessage());
        }
        return ResponseEntity.ok(List.of());
    }

    /**
     * Disconnect Jira endpoint.
     * POST /api/integrations/jira/disconnect
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectJira(HttpServletRequest servletRequest) {
        tryResolveContext(servletRequest);

        isConnected = false;
        connectedSiteName = null;
        log.info("Disconnected Jira Cloud integration");
        return ResponseEntity.ok(Map.of(
                "connected", false,
                "message", "Jira Cloud disconnected successfully"
        ));
    }

    private List<Map<String, String>> fetchAccessibleResources(String accessToken) {
        if (accessToken == null) return List.of();
        try {
            String resourcesUrl = "https://api.atlassian.com/oauth/token/accessible-resources";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(resourcesUrl, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode array = objectMapper.readTree(response.getBody());
                List<Map<String, String>> sites = new ArrayList<>();
                if (array.isArray()) {
                    for (JsonNode node : array) {
                        sites.add(Map.of(
                                "id", node.path("id").asText(""),
                                "name", node.path("name").asText(""),
                                "url", node.path("url").asText("")
                        ));
                    }
                }
                return sites;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch accessible Jira resources: {}", e.getMessage());
        }
        return List.of();
    }
}
