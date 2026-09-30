package com.devpulse.integration.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * All outbound HTTP calls to Atlassian for the Jira Cloud OAuth 2.0 (3LO)
 * integration: token exchange/refresh, site discovery, and reading issues
 * through the api.atlassian.com proxy. Nothing here touches the database —
 * callers persist whatever they need.
 */
@Component
public class JiraCloudClient {

    private static final String TOKEN_URL = "https://auth.atlassian.com/oauth/token";
    private static final String API_BASE = "https://api.atlassian.com";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ATLASSIAN_CLIENT_ID:devpulse-jira-client-id}")
    private String clientId;

    @Value("${ATLASSIAN_CLIENT_SECRET:}")
    private String clientSecret;

    public JiraCloudClient(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /** One field of Atlassian's token response: the parts callers need to persist. */
    public record TokenResult(String accessToken, String refreshToken) {
    }

    /** One entry from GET /oauth/token/accessible-resources. */
    public record AccessibleSite(String id, String name, String url) {
    }

    public record JiraProjectSummary(String id, String key, String name) {
    }

    /** One issue as read back from the search endpoint, ready to upsert. */
    public record JiraIssueData(
            String key, String summary, String issueType, String priority, String status,
            Integer storyPoints, String projectKey, String assigneeEmail, boolean done) {
    }

    public TokenResult exchangeCodeForToken(String code, String redirectUri) {
        Map<String, String> body = Map.of(
                "grant_type", "authorization_code",
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code,
                "redirect_uri", redirectUri);
        JsonNode response = postToken(body);
        return new TokenResult(
                response.path("access_token").asText(null),
                response.path("refresh_token").asText(null));
    }

    /**
     * Refresh tokens are single-use and rotate: the response always carries a
     * new refresh_token that callers must persist in place of the old one.
     */
    public TokenResult refreshAccessToken(String refreshToken) {
        Map<String, String> body = Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "client_secret", clientSecret,
                "refresh_token", refreshToken);
        JsonNode response = postToken(body);
        return new TokenResult(
                response.path("access_token").asText(null),
                response.path("refresh_token").asText(refreshToken));
    }

    private JsonNode postToken(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    TOKEN_URL, new HttpEntity<>(body, headers), String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new JiraApiException("Failed to obtain a token from Atlassian: " + e.getMessage(), e);
        }
    }

    public List<AccessibleSite> fetchAccessibleResources(String accessToken) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    API_BASE + "/oauth/token/accessible-resources",
                    HttpMethod.GET, bearerEntity(accessToken), String.class);
            JsonNode array = objectMapper.readTree(response.getBody());
            List<AccessibleSite> sites = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode node : array) {
                    sites.add(new AccessibleSite(
                            node.path("id").asText(""),
                            node.path("name").asText(""),
                            node.path("url").asText("")));
                }
            }
            return sites;
        } catch (Exception e) {
            throw new JiraApiException("Failed to fetch accessible Jira resources: " + e.getMessage(), e);
        }
    }

    public List<JiraProjectSummary> fetchProjects(String cloudId, String accessToken) {
        String url = API_BASE + "/ex/jira/" + cloudId + "/rest/api/3/project/search?maxResults=100";
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, bearerEntity(accessToken), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            List<JiraProjectSummary> projects = new ArrayList<>();
            for (JsonNode node : root.path("values")) {
                projects.add(new JiraProjectSummary(
                        node.path("id").asText(""),
                        node.path("key").asText(""),
                        node.path("name").asText("")));
            }
            return projects;
        } catch (Exception e) {
            throw new JiraApiException("Failed to fetch Jira projects: " + e.getMessage(), e);
        }
    }

    /**
     * One page of issues via JQL search, newest-updated first. Callers loop on
     * {@code startAt} using the returned list's size against {@code maxResults}
     * to know whether another page exists.
     */
    public List<JiraIssueData> searchIssues(String cloudId, String accessToken, int startAt, int maxResults) {
        String url = UriComponentsBuilder
                .fromHttpUrl(API_BASE + "/ex/jira/" + cloudId + "/rest/api/3/search")
                .queryParam("jql", "ORDER BY updated DESC")
                .queryParam("startAt", startAt)
                .queryParam("maxResults", maxResults)
                .queryParam("fields", "summary,issuetype,priority,status,project,assignee,customfield_10016")
                .build()
                .toUriString();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, bearerEntity(accessToken), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            List<JiraIssueData> issues = new ArrayList<>();
            for (JsonNode issue : root.path("issues")) {
                JsonNode fields = issue.path("fields");
                String status = fields.path("status").path("name").asText("To Do");
                String statusCategory = fields.path("status").path("statusCategory").path("key").asText("");
                issues.add(new JiraIssueData(
                        issue.path("key").asText(null),
                        fields.path("summary").asText(""),
                        fields.path("issuetype").path("name").asText("Task"),
                        fields.path("priority").path("name").asText("Medium"),
                        status,
                        fields.path("customfield_10016").isInt() ? fields.path("customfield_10016").asInt() : null,
                        fields.path("project").path("key").asText(null),
                        fields.path("assignee").path("emailAddress").asText(null),
                        "done".equalsIgnoreCase(statusCategory)));
            }
            return issues;
        } catch (Exception e) {
            throw new JiraApiException("Failed to search Jira issues (startAt=" + startAt + "): " + e.getMessage(), e);
        }
    }

    private HttpEntity<Void> bearerEntity(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/json");
        return new HttpEntity<>(headers);
    }

    /** True when the underlying failure was a 401 — the caller's cue to refresh and retry once. */
    public static boolean isUnauthorized(JiraApiException e) {
        return e.getCause() instanceof org.springframework.web.client.HttpClientErrorException.Unauthorized;
    }

    public static class JiraApiException extends RuntimeException {
        public JiraApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
