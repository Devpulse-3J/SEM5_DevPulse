package com.devpulse.integration.controller;

import com.devpulse.integration.dto.GithubStatusResponse;
import com.devpulse.integration.dto.LinkGithubRequest;
import com.devpulse.integration.dto.LinkGithubResponse;
import com.devpulse.integration.security.RequestContext;
import com.devpulse.integration.security.RequestContextResolver;
import com.devpulse.integration.service.ProjectGithubLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A project's GitHub connection. Admin only, company scoped.
 *
 * <p>Mapped under {@code /integrations/projects/**} rather than
 * {@code /projects/**} so it stays inside the gateway's existing
 * {@code /api/integrations/**} route. Putting it on {@code /projects/**} would
 * collide with auth-service's route: Spring Cloud Gateway takes the first
 * matching route in declaration order, and {@code /api/projects/**} sends
 * everything to auth-service.
 *
 * <p>Public paths are therefore:
 * <pre>
 *   POST /api/integrations/projects/{id}/github/link
 *   GET  /api/integrations/projects/{id}/github/status
 *   POST /api/integrations/projects/{id}/github/sync
 * </pre>
 */
@RestController
@RequestMapping("/integrations/projects/{projectId}/github")
public class ProjectGithubController {

    private final ProjectGithubLinkService projectGithubLinkService;
    private final RequestContextResolver contextResolver;

    public ProjectGithubController(ProjectGithubLinkService projectGithubLinkService,
                                   RequestContextResolver contextResolver) {
        this.projectGithubLinkService = projectGithubLinkService;
        this.contextResolver = contextResolver;
    }

    /**
     * Links a repository to the project.
     * Body: {@code { "repoUrl": "https://github.com/owner/repo", "webhookSecret": "..." }}.
     */
    @PostMapping("/link")
    public ResponseEntity<LinkGithubResponse> link(
            HttpServletRequest servletRequest,
            @PathVariable("projectId") Integer projectId,
            @Valid @RequestBody LinkGithubRequest request) {
        RequestContext context = contextResolver.resolve(servletRequest);
        return ResponseEntity.ok(projectGithubLinkService.link(context, projectId, request));
    }

    /** Reports whether a repository is linked, and when it last synced. */
    @GetMapping("/status")
    public ResponseEntity<GithubStatusResponse> status(
            HttpServletRequest servletRequest,
            @PathVariable("projectId") Integer projectId) {
        RequestContext context = contextResolver.resolve(servletRequest);
        return ResponseEntity.ok(projectGithubLinkService.status(context, projectId));
    }

    /** Triggers the initial backfill. Logs only — see the service for why. */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
            HttpServletRequest servletRequest,
            @PathVariable("projectId") Integer projectId) {
        RequestContext context = contextResolver.resolve(servletRequest);
        return ResponseEntity.accepted().body(projectGithubLinkService.sync(context, projectId));
    }
}
