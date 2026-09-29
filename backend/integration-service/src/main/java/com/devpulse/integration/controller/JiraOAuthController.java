package com.devpulse.integration.controller;

import com.devpulse.integration.entity.JiraConnection;
import com.devpulse.integration.jira.JiraCloudClient;
import com.devpulse.integration.jira.JiraIssueSyncService;
import com.devpulse.integration.jira.JiraOAuthState;
import com.devpulse.integration.repository.JiraConnectionRepository;
import com.devpulse.integration.repository.JiraIssueRepository;
import com.devpulse.integration.security.RequestContext;
import com.devpulse.integration.security.RequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller handling Atlassian OAuth 2.0 (3LO) 1-click Jira Workspace installation
 * and token authorization callback, plus reading real project/issue data from the
 * connected site.
 */
@RestController
@RequestMapping("/integrations/jira")
public class JiraOAuthController {

    private static final Logger log = LoggerFactory.getLogger(JiraOAuthController.class);

    private final JiraCloudClient client;
    private final JiraOAuthState oauthState;
    private final JiraConnectionRepository connectionRepository;
    private final JiraIssueRepository jiraIssueRepository;
    private final JiraIssueSyncService syncService;
    private final RequestContextResolver contextResolver;
    private final String redirectUri;
    private final String frontendBaseUrl;

    @Value("${ATLASSIAN_CLIENT_ID:devpulse-jira-client-id}")
    private String clientId;

    public JiraOAuthController(
            JiraCloudClient client,
            JiraOAuthState oauthState,
            JiraConnectionRepository connectionRepository,
            JiraIssueRepository jiraIssueRepository,
            JiraIssueSyncService syncService,
            RequestContextResolver contextResolver,
            @Value("${ATLASSIAN_REDIRECT_URI:}") String redirectUri,
            @Value("${FRONTEND_BASE_URL:}") String frontendBaseUrl) {
        this.client = client;
        this.oauthState = oauthState;
        this.connectionRepository = connectionRepository;
        this.jiraIssueRepository = jiraIssueRepository;
        this.syncService = syncService;
        this.contextResolver = contextResolver;

        String cleanFrontendUrl = (frontendBaseUrl != null && !frontendBaseUrl.isBlank())
                ? frontendBaseUrl.replaceAll("/+$", "")
                : "http://localhost:3000";
        this.frontendBaseUrl = cleanFrontendUrl;

        this.redirectUri = (redirectUri != null && !redirectUri.isBlank())
                ? redirectUri
                : cleanFrontendUrl + "/api/integrations/jira/oauth/callback";
    }

    /**
     * Generates Atlassian OAuth 2.0 3LO Authorization URL.
     * GET /api/integrations/jira/oauth/install
     *
     * <p>This path is public at the gateway (Atlassian's callback redirect can't
     * carry our JWT), so companyId/userId come from the caller as query params —
     * the frontend already has both from the signed-in user's profile — and are
     * embedded in a signed {@code state} the callback verifies before trusting them.
     */
    @GetMapping("/oauth/install")
    public ResponseEntity<Map<String, String>> getOAuthInstallUrl(
            @RequestParam Integer companyId,
            @RequestParam(required = false) Integer userId) {

        String scope = "read:jira-work read:jira-user offline_access";
        String encodedScope = URLEncoder.encode(scope, StandardCharsets.UTF_8);
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String state = oauthState.sign(companyId, userId);
        String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);

        String authUrl = String.format(
                "https://auth.atlassian.com/authorize?audience=api.atlassian.com&client_id=%s&scope=%s&redirect_uri=%s&state=%s&response_type=code&prompt=consent",
                clientId, encodedScope, encodedRedirect, encodedState);

        return ResponseEntity.ok(Map.of("installUrl", authUrl, "url", authUrl, "status", "ok"));
    }

    /**
     * Handles Atlassian OAuth 2.0 Callback.
     * GET /api/integrations/jira/oauth/callback?code={code}&state={state}
     */
    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> handleOAuthCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) {

        Optional<JiraOAuthState.Claims> claims = oauthState.verify(state);

        if (error != null || code == null || code.isBlank() || claims.isEmpty()) {
            log.warn("Jira OAuth callback rejected: error={}, hasCode={}, validState={}",
                    error, code != null, claims.isPresent());
            return redirectTo(frontendBaseUrl + "/admin/integrations/jira?jira=error");
        }

        Integer companyId = claims.get().companyId();
        Integer userId = claims.get().userId();

        try {
            JiraCloudClient.TokenResult token = client.exchangeCodeForToken(code, redirectUri);
            if (token.accessToken() == null) {
                throw new IllegalStateException("Atlassian returned no access_token");
            }

            List<JiraCloudClient.AccessibleSite> sites = client.fetchAccessibleResources(token.accessToken());
            if (sites.isEmpty()) {
                throw new IllegalStateException("No accessible Jira sites for this account");
            }
            JiraCloudClient.AccessibleSite site = sites.get(0);

            JiraConnection connection = connectionRepository.findByCompanyId(companyId)
                    .orElseGet(JiraConnection::new);
            connection.setCompanyId(companyId);
            connection.setCloudId(site.id());
            connection.setSiteUrl(site.url());
            connection.setSiteName(site.name());
            connection.setAccessToken(token.accessToken());
            connection.setRefreshToken(token.refreshToken());
            connection.setConnectedByUserId(userId != null && userId > 0 ? userId : null);
            connection.setUpdatedAt(Instant.now());
            connection.setActive(true);
            connection = connectionRepository.save(connection);

            log.info("Company {} connected Jira site {} ({})", companyId, site.name(), site.id());

            // Best-effort initial backfill: a slow/failed sync must never break
            // the connection itself, since the site is already saved above.
            try {
                int synced = syncService.sync(connection);
                log.info("Initial Jira sync for company {} upserted {} issues", companyId, synced);
            } catch (Exception syncError) {
                log.warn("Initial Jira sync failed for company {}: {}", companyId, syncError.getMessage());
            }

            return redirectTo(frontendBaseUrl + "/admin/integrations/jira?jira=success");
        } catch (Exception e) {
            log.error("Jira OAuth callback failed for company {}: {}", companyId, e.getMessage());
            return redirectTo(frontendBaseUrl + "/admin/integrations/jira?jira=error");
        }
    }

    private ResponseEntity<Void> redirectTo(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    /**
     * Endpoint to retrieve available Jira projects for project linking dropdowns.
     * GET /api/integrations/jira/available-projects
     */
    @GetMapping("/available-projects")
    public ResponseEntity<Map<String, Object>> getAvailableProjects(HttpServletRequest servletRequest) {
        RequestContext ctx = contextResolver.resolve(servletRequest);
        Optional<JiraConnection> connection = connectionRepository.findByCompanyIdAndActiveTrue(ctx.companyId());

        Map<String, Object> response = new HashMap<>();
        if (connection.isEmpty()) {
            response.put("connected", false);
            response.put("projects", List.of());
            return ResponseEntity.ok(response);
        }

        List<Map<String, String>> projects = new ArrayList<>();
        try {
            for (JiraCloudClient.JiraProjectSummary p : client.fetchProjects(
                    connection.get().getCloudId(), connection.get().getAccessToken())) {
                projects.add(Map.of("id", p.id(), "key", p.key(), "name", p.name()));
            }
        } catch (Exception e) {
            log.warn("Failed to load real Jira projects for company {}: {}", ctx.companyId(), e.getMessage());
        }

        response.put("connected", true);
        response.put("projects", projects);
        return ResponseEntity.ok(response);
    }

    /**
     * Connection status endpoint.
     * GET /api/integrations/jira/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getJiraStatus(HttpServletRequest servletRequest) {
        RequestContext ctx = contextResolver.resolve(servletRequest);
        Optional<JiraConnection> connection = connectionRepository.findByCompanyIdAndActiveTrue(ctx.companyId());

        Map<String, Object> response = new HashMap<>();
        boolean connected = connection.isPresent();
        response.put("connected", connected);
        response.put("provider", "jira");
        if (connected) {
            response.put("siteName", connection.get().getSiteName());
        }
        response.put("message", connected ? "Jira Cloud connected" : "Jira Cloud not connected");
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to retrieve stored Jira issues from Supabase DB scoped by company.
     * GET /api/integrations/jira/issues
     */
    @GetMapping("/issues")
    public ResponseEntity<List<com.devpulse.integration.entity.JiraIssue>> getStoredIssues(
            HttpServletRequest servletRequest) {
        try {
            RequestContext context = contextResolver.resolve(servletRequest);
            return ResponseEntity.ok(jiraIssueRepository.findByCompanyId(context.companyId()));
        } catch (Exception e) {
            log.debug("No gateway identity headers on getStoredIssues: {}", e.getMessage());
        }
        return ResponseEntity.ok(List.of());
    }

    /**
     * Re-runs the issue sync on demand for the caller's connected site.
     * POST /api/integrations/jira/sync
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncNow(HttpServletRequest servletRequest) {
        RequestContext ctx = contextResolver.resolve(servletRequest);
        JiraConnection connection = connectionRepository.findByCompanyIdAndActiveTrue(ctx.companyId())
                .orElse(null);
        if (connection == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Jira is not connected for this company"));
        }
        int synced = syncService.sync(connection);
        return ResponseEntity.ok(Map.of("status", "ok", "issuesSynced", synced));
    }

    /**
     * Disconnect Jira endpoint.
     * POST /api/integrations/jira/disconnect
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectJira(HttpServletRequest servletRequest) {
        RequestContext ctx = contextResolver.resolve(servletRequest);
        connectionRepository.findByCompanyId(ctx.companyId()).ifPresent(connection -> {
            connection.setActive(false);
            connection.setUpdatedAt(Instant.now());
            connectionRepository.save(connection);
        });
        log.info("Company {} disconnected Jira Cloud integration", ctx.companyId());
        return ResponseEntity.ok(Map.of("connected", false, "message", "Jira Cloud disconnected successfully"));
    }
}
