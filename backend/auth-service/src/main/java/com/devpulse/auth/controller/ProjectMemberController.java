package com.devpulse.auth.controller;

import com.devpulse.auth.dto.AddProjectMemberRequest;
import com.devpulse.auth.dto.ChangeMemberRoleRequest;
import com.devpulse.auth.dto.InviteByEmailRequest;
import com.devpulse.auth.dto.InviteResultResponse;
import com.devpulse.auth.dto.ProjectMemberResponse;
import com.devpulse.auth.security.RequestContext;
import com.devpulse.auth.security.RequestContextResolver;
import com.devpulse.auth.service.ProjectMemberService;
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
 * Project membership and invitations.
 *
 * <p>Mapped under {@code /projects/{projectId}} so it shares the gateway's
 * {@code /api/projects/**} route with {@link ProjectController}.
 */
@RestController
@RequestMapping("/projects/{projectId}")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;
    private final RequestContextResolver contextResolver;

    public ProjectMemberController(ProjectMemberService projectMemberService,
                                   RequestContextResolver contextResolver) {
        this.projectMemberService = projectMemberService;
        this.contextResolver = contextResolver;
    }

    /** {@code GET /projects/{id}/members} — admin, or a member of the project. */
    @GetMapping("/members")
    public ResponseEntity<List<ProjectMemberResponse>> listMembers(
            HttpServletRequest servletRequest,
            @PathVariable("projectId") Integer projectId) {
        RequestContext context = contextResolver.resolve(servletRequest);
        return ResponseEntity.ok(projectMemberService.listMembers(context, projectId));
    }

    /**
     * {@code POST /projects/{id}/members} — admin only.
     * Body: {@code { "userId": 7, "role": "MANAGER" }}.
     */
    @PostMapping("/members")
    public ResponseEntity<ProjectMemberResponse> addMember(
            HttpServletRequest servletRequest,
            @PathVariable("projectId") Integer projectId,
            @Valid @RequestBody AddProjectMemberRequest request) {
        RequestContext context = contextResolver.resolve(servletRequest);
        ProjectMemberResponse response =
                projectMemberService.addMember(context, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * {@code PUT /projects/{id}/members/{userId}} — admin only.
     * Body: {@code { "role": "DEVELOPER" }}.
     */
    @PutMapping("/members/{userId}")
    public ResponseEntity<ProjectMemberResponse> changeRole(
            HttpServletRequest servletRequest,
            @PathVariable("projectId") Integer projectId,
            @PathVariable("userId") Integer userId,
            @Valid @RequestBody ChangeMemberRoleRequest request) {
        RequestContext context = contextResolver.resolve(servletRequest);
        return ResponseEntity.ok(
                projectMemberService.changeRole(context, projectId, userId, request));
    }

    /** {@code DELETE /projects/{id}/members/{userId}} — admin only. */
    @DeleteMapping("/members/{userId}")
    public ResponseEntity<Void> removeMember(
            HttpServletRequest servletRequest,
            @PathVariable("projectId") Integer projectId,
            @PathVariable("userId") Integer userId) {
        RequestContext context = contextResolver.resolve(servletRequest);
        projectMemberService.removeMember(context, projectId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code POST /projects/{id}/invite} — admin only.
     * Body: {@code { "email": "...", "role": "MANAGER" }}.
     *
     * <p>Adds the person straight away if the email belongs to an account in this
     * company. An admin may only invite someone who has already registered, so an
     * unknown address is a 404 and an address owned by another company is a 409.
     * This endpoint never creates a user.
     */
    @PostMapping("/invite")
    public ResponseEntity<InviteResultResponse> invite(
            HttpServletRequest servletRequest,
            @PathVariable("projectId") Integer projectId,
            @Valid @RequestBody InviteByEmailRequest request) {
        RequestContext context = contextResolver.resolve(servletRequest);
        InviteResultResponse response =
                projectMemberService.inviteByEmail(context, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
