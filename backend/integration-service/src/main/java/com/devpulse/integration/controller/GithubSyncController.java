package com.devpulse.integration.controller;

import com.devpulse.integration.service.GithubHistoricalSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller exposing endpoints to trigger real-time and historical
 * synchronization with GitHub REST API.
 */
@RestController
@RequestMapping("/api/integrations/github")
public class GithubSyncController {

    private static final Logger log = LoggerFactory.getLogger(GithubSyncController.class);

    private final GithubHistoricalSyncService githubHistoricalSyncService;

    public GithubSyncController(GithubHistoricalSyncService githubHistoricalSyncService) {
        this.githubHistoricalSyncService = githubHistoricalSyncService;
    }

    /**
     * Triggers a historical import/backfill of pull requests, commits, and repo details
     * from GitHub REST API for a specific owner/repo repository.
     *
     * @param owner Repository owner username or organization (e.g. "octocat")
     * @param repo Repository name (e.g. "Hello-World")
     * @param companyId Tenant company context ID (defaults to 1)
     * @param projectId Project mapping ID (defaults to 1)
     * @return Sync summary status response
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncHistoricalData(
            @RequestParam("owner") String owner,
            @RequestParam("repo") String repo,
            @RequestParam(value = "companyId", defaultValue = "1") Integer companyId,
            @RequestParam(value = "projectId", defaultValue = "1") Integer projectId) {

        log.info("Request received to trigger historical GitHub sync for owner: {}, repo: {}, companyId: {}", owner, repo, companyId);
        Map<String, Object> result = githubHistoricalSyncService.syncHistoricalProjectData(owner, repo, companyId, projectId);
        return ResponseEntity.ok(result);
    }
}
