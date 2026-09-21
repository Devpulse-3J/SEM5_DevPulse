package com.devpulse.auth.controller;

import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.service.ProjectInvitationClaimService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class ProjectInvitationController {

    private final ProjectInvitationClaimService claimService;

    public ProjectInvitationController(ProjectInvitationClaimService claimService) {
        this.claimService = claimService;
    }

    /**
     * A signed-in user accepts the project invitation emailed to them.
     * POST /auth/invitations/project/accept?token={token}
     */
    @PostMapping("/invitations/project/accept")
    public ResponseEntity<Map<String, Object>> acceptProjectInvitation(
            @AuthenticationPrincipal User user,
            @RequestParam("token") String token) {
        ProjectMember membership = claimService.accept(token, user);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "projectId", membership.getProjectId(),
                "role", membership.getRole()
        ));
    }
}
