package com.devpulse.auth.service;

import com.devpulse.auth.dto.AddProjectMemberRequest;
import com.devpulse.auth.dto.ChangeMemberRoleRequest;
import com.devpulse.auth.dto.InviteByEmailRequest;
import com.devpulse.auth.dto.InviteResultResponse;
import com.devpulse.auth.dto.ProjectMemberResponse;
import com.devpulse.auth.security.RequestContext;
import java.util.List;

/**
 * Membership management for a single project. Every mutating method is admin
 * only; the project must belong to the caller's company.
 */
public interface ProjectMemberService {

    /** Admin, or a member of the project. */
    List<ProjectMemberResponse> listMembers(RequestContext context, Integer projectId);

    /** Admin only. The target user must already exist in the same company. */
    ProjectMemberResponse addMember(RequestContext context, Integer projectId,
                                    AddProjectMemberRequest request);

    /** Admin only. */
    ProjectMemberResponse changeRole(RequestContext context, Integer projectId,
                                     Integer userId, ChangeMemberRoleRequest request);

    /** Admin only. */
    void removeMember(RequestContext context, Integer projectId, Integer userId);

    /** Admin only. Creates the account if the email has none. */
    InviteResultResponse inviteByEmail(RequestContext context, Integer projectId,
                                       InviteByEmailRequest request);
}
