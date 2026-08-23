package com.devpulse.auth.service;

import com.devpulse.auth.dto.*;
import com.devpulse.auth.entity.*;

import java.util.List;

public interface WorkspaceInviteService {

    OrganizationInvitation inviteToOrganization(User actor, OrganizationInviteRequest request);

    User acceptOrganizationInvite(String token, User user);

    ProjectMember inviteToProject(User actor, Integer projectId, ProjectInviteRequest request);

    WorkspaceJoinRequest requestToJoinWorkspace(User actor, Integer companyId, JoinWorkspaceRequestDto request);

    List<WorkspaceJoinRequest> getPendingJoinRequests(User actor, Integer companyId);

    WorkspaceJoinRequest approveJoinRequest(User actor, Integer companyId, Integer requestId);

    WorkspaceJoinRequest rejectJoinRequest(User actor, Integer companyId, Integer requestId);

    List<Company> searchWorkspaces(String query);
}
