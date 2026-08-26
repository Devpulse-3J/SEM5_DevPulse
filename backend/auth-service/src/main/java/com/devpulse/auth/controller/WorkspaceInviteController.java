package com.devpulse.auth.controller;

import com.devpulse.auth.dto.*;
import com.devpulse.auth.entity.*;
import com.devpulse.auth.service.WorkspaceInviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller providing REST endpoints for Organization Admin invitations,
 * Manager project member invites, and GitHub user join request approval workflows.
 */
@RestController
@RequestMapping("/auth")
public class WorkspaceInviteController {

    private final WorkspaceInviteService workspaceInviteService;

    public WorkspaceInviteController(WorkspaceInviteService workspaceInviteService) {
        this.workspaceInviteService = workspaceInviteService;
    }

    /**
     * Admin invites a team member to join the Company Organization workspace.
     * POST /auth/invitations/organization
     */
    @PostMapping("/invitations/organization")
    public ResponseEntity<OrganizationInvitation> inviteToOrganization(
            @AuthenticationPrincipal User actor,
            @Valid @RequestBody OrganizationInviteRequest request) {
        OrganizationInvitation invitation = workspaceInviteService.inviteToOrganization(actor, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
    }

    /**
     * User accepts an organization invitation using an invite token.
     * POST /auth/invitations/organization/accept?token={token}
     */
    @PostMapping("/invitations/organization/accept")
    public ResponseEntity<Map<String, Object>> acceptOrganizationInvite(
            @AuthenticationPrincipal User user,
            @RequestParam("token") String token) {
        User updatedUser = workspaceInviteService.acceptOrganizationInvite(token, user);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Successfully joined company workspace",
                "companyId", updatedUser.getCompany().getCompanyId()
        ));
    }

    /**
     * Project Manager or Admin invites a user to join a specific Project Workspace.
     * POST /auth/projects/{projectId}/invite
     */
    @PostMapping("/projects/{projectId}/invite")
    public ResponseEntity<ProjectMember> inviteToProject(
            @AuthenticationPrincipal User actor,
            @PathVariable("projectId") Integer projectId,
            @Valid @RequestBody ProjectInviteRequest request) {
        ProjectMember membership = workspaceInviteService.inviteToProject(actor, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(membership);
    }

    /**
     * GitHub User / user submits a Request to Join an Organization Workspace.
     * POST /auth/workspaces/{companyId}/join-requests
     */
    @PostMapping("/workspaces/{companyId}/join-requests")
    public ResponseEntity<WorkspaceJoinRequest> requestToJoinWorkspace(
            @AuthenticationPrincipal User actor,
            @PathVariable("companyId") Integer companyId,
            @RequestBody JoinWorkspaceRequestDto request) {
        WorkspaceJoinRequest joinRequest = workspaceInviteService.requestToJoinWorkspace(actor, companyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(joinRequest);
    }

    /**
     * Organization Admin lists all pending Join Requests for their company.
     * GET /auth/workspaces/{companyId}/join-requests
     */
    @GetMapping("/workspaces/{companyId}/join-requests")
    public ResponseEntity<List<WorkspaceJoinRequest>> getPendingJoinRequests(
            @AuthenticationPrincipal User actor,
            @PathVariable("companyId") Integer companyId) {
        List<WorkspaceJoinRequest> pendingRequests = workspaceInviteService.getPendingJoinRequests(actor, companyId);
        return ResponseEntity.ok(pendingRequests);
    }

    /**
     * Organization Admin approves a user's Join Workspace Request.
     * POST /auth/workspaces/{companyId}/join-requests/{requestId}/approve
     */
    @PostMapping("/workspaces/{companyId}/join-requests/{requestId}/approve")
    public ResponseEntity<WorkspaceJoinRequest> approveJoinRequest(
            @AuthenticationPrincipal User actor,
            @PathVariable("companyId") Integer companyId,
            @PathVariable("requestId") Integer requestId) {
        WorkspaceJoinRequest approvedRequest = workspaceInviteService.approveJoinRequest(actor, companyId, requestId);
        return ResponseEntity.ok(approvedRequest);
    }

    /**
     * Organization Admin rejects a user's Join Workspace Request.
     * POST /auth/workspaces/{companyId}/join-requests/{requestId}/reject
     */
    @PostMapping("/workspaces/{companyId}/join-requests/{requestId}/reject")
    public ResponseEntity<WorkspaceJoinRequest> rejectJoinRequest(
            @AuthenticationPrincipal User actor,
            @PathVariable("companyId") Integer companyId,
            @PathVariable("requestId") Integer requestId) {
        WorkspaceJoinRequest rejectedRequest = workspaceInviteService.rejectJoinRequest(actor, companyId, requestId);
        return ResponseEntity.ok(rejectedRequest);
    }

    /**
     * Search company workspaces by name (for individual users looking to join a company).
     * GET /auth/workspaces/search?query={name}
     */
    @GetMapping("/workspaces/search")
    public ResponseEntity<List<Company>> searchWorkspaces(
            @RequestParam(value = "query", required = false) String query) {
        List<Company> companies = workspaceInviteService.searchWorkspaces(query);
        return ResponseEntity.ok(companies);
    }
}
