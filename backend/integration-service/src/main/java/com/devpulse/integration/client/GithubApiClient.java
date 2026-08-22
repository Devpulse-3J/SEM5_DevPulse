package com.devpulse.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Client for interacting directly with the GitHub REST API (api.github.com).
 * Supports authenticated requests via Personal Access Tokens (PAT) or App Tokens.
 */
@Component
public class GithubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GithubApiClient.class);
    private static final String DEFAULT_GITHUB_API_BASE_URL = "https://api.github.com";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String githubToken;

    public GithubApiClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${github.api.baseUrl:https://api.github.com}") String baseUrl,
            @Value("${github.api.token:}") String githubToken) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_GITHUB_API_BASE_URL;
        this.githubToken = githubToken;
    }

    /**
     * Fetches repository metadata from GitHub REST API.
     * GET /repos/{owner}/{repo}
     */
    public JsonNode fetchRepositoryDetails(String owner, String repo) {
        String url = String.format("%s/repos/%s/%s", baseUrl, owner, repo);
        return executeGetRequest(url);
    }

    /**
     * Fetches pull requests for a repository.
     * GET /repos/{owner}/{repo}/pulls?state={state}&per_page=100
     */
    public JsonNode fetchPullRequests(String owner, String repo, String state) {
        String reqState = (state != null && !state.isBlank()) ? state : "all";
        String url = String.format("%s/repos/%s/%s/pulls?state=%s&per_page=100", baseUrl, owner, repo, reqState);
        return executeGetRequest(url);
    }

    /**
     * Fetches commits for a repository.
     * GET /repos/{owner}/{repo}/commits?per_page=100
     */
    public JsonNode fetchCommits(String owner, String repo) {
        String url = String.format("%s/repos/%s/%s/commits?per_page=100", baseUrl, owner, repo);
        return executeGetRequest(url);
    }

    /**
     * Fetches issues for a repository.
     * GET /repos/{owner}/{repo}/issues?state={state}&per_page=100
     */
    public JsonNode fetchIssues(String owner, String repo, String state) {
        String reqState = (state != null && !state.isBlank()) ? state : "all";
        String url = String.format("%s/repos/%s/%s/issues?state=%s&per_page=100", baseUrl, owner, repo, reqState);
        return executeGetRequest(url);
    }

    /**
     * Registers a push/pull_request webhook on a repository.
     * <p>
     * <b>MOCKED.</b> This logs the call it would make and returns false; it does
     * not contact GitHub. Registering a hook needs a token with {@code admin:repo_hook}
     * scope and a publicly reachable callback URL, neither of which is configured
     * yet ({@code github.api.token} is empty by default, and the service runs on
     * localhost). Until this is real, the hook must be added by hand in the
     * repository's Settings → Webhooks, pointing at {@code /api/webhooks/github}
     * with the same secret stored on the repo row.
     *
     * @return true only if a webhook was really created — always false today
     */
    public boolean createWebhook(String owner, String repo, String secret, String callbackUrl) {
        log.warn("[MOCK] Would POST {}/repos/{}/{}/hooks with callback '{}' and a {} secret. "
                        + "No request was made — register this webhook manually.",
                baseUrl, owner, repo, callbackUrl,
                secret == null || secret.isBlank() ? "service-wide" : "per-repo");
        return false;
    }

    private JsonNode executeGetRequest(String url) {
        log.info("Executing GitHub REST API GET request to: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("User-Agent", "DevPulse-Integration-Service");

        if (githubToken != null && !githubToken.isBlank()) {
            String authHeader = githubToken.startsWith("Bearer ") || githubToken.startsWith("token ")
                    ? githubToken
                    : "Bearer " + githubToken;
            headers.set("Authorization", authHeader);
        } else {
            log.warn("No GitHub token configured. Unauthenticated requests to GitHub API are rate-limited to 60 req/hr.");
        }

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readTree(response.getBody());
            } else {
                log.warn("GitHub API call returned non-2xx status code: {} for URL: {}", response.getStatusCode(), url);
                return objectMapper.createArrayNode();
            }
        } catch (Exception e) {
            log.error("Failed to execute GitHub API request to '{}': {}", url, e.getMessage());
            return objectMapper.createArrayNode();
        }
    }
}
