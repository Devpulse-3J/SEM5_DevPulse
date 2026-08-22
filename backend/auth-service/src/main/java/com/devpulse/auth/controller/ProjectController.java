package com.devpulse.auth.controller;

import com.devpulse.auth.dto.CreateProjectRequest;
import com.devpulse.auth.dto.ProjectResponse;
import com.devpulse.auth.dto.UpdateProjectRequest;
import com.devpulse.auth.security.RequestContext;
import com.devpulse.auth.security.RequestContextResolver;
import com.devpulse.auth.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project CRUD.
 *
 * <p>No {@code /api} prefix: the gateway matches {@code /api/projects/**} and
 * applies {@code StripPrefix=1}, so it forwards {@code /projects/...}. Mapping
 * {@code /api/...} here would mean the forwarded path never matches — the same
 * mistake documented on {@code GithubSyncController}.
 *
 * <p>Identity comes from the gateway's {@code X-User-Id} / {@code X-Company-Id}
 * headers via {@link RequestContextResolver}; the role and tenancy checks live
 * in the service layer so they cannot be skipped by a new caller.
 */
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final RequestContextResolver contextResolver;

    public ProjectController(ProjectService projectService,
                             RequestContextResolver contextResolver) {
        this.projectService = projectService;
        this.contextResolver = contextResolver;
    }

    /** {@code POST /projects} — admin only. */
    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            HttpServletRequest servletRequest,
            @Valid @RequestBody CreateProjectRequest request) {
        RequestContext context = contextResolver.resolve(servletRequest);
        ProjectResponse response = projectService.create(context, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** {@code GET /projects} — every project in the company for an admin, own projects otherwise. */
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> list(HttpServletRequest servletRequest) {
        RequestContext context = contextResolver.resolve(servletRequest);
        return ResponseEntity.ok(projectService.list(context));
    }

    /** {@code GET /projects/{id}} — admin, or a member of the project. */
    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> get(
            HttpServletRequest servletRequest,
            @PathVariable("projectId") Integer projectId) {
        RequestContext context = contextResolver.resolve(servletRequest);
        return ResponseEntity.ok(projectService.get(context, projectId));
    }

    /** {@code PUT /projects/{id}} — admin only. Full replacement, not a patch. */
    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> update(
            HttpServletRequest servletRequest,
            @PathVariable("projectId") Integer projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        RequestContext context = contextResolver.resolve(servletRequest);
        return ResponseEntity.ok(projectService.update(context, projectId, request));
    }

    /** {@code DELETE /projects/{id}} — admin only. */
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(
            HttpServletRequest servletRequest,
            @PathVariable("projectId") Integer projectId) {
        RequestContext context = contextResolver.resolve(servletRequest);
        projectService.delete(context, projectId);
        return ResponseEntity.noContent().build();
    }
}
