package com.devpulse.auth.dto;

import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.User;
import java.time.OffsetDateTime;

/**
 * API view of a project membership, joined with the user it belongs to.
 *
 * <p>{@code role} is returned uppercase — the API's form — even though the
 * database stores it lowercase.
 */
public class ProjectMemberResponse {

    private Integer membershipId;
    private Integer userId;
    private String email;
    private String fullName;
    private String role;
    private OffsetDateTime joinedAt;

    /**
     * True while the account was created by an invitation and its owner has not
     * set a password yet. The frontend renders this as "Invited (pending)".
     */
    private boolean pending;

    public ProjectMemberResponse() {
    }

    public static ProjectMemberResponse from(ProjectMember membership, User user) {
        ProjectMemberResponse response = new ProjectMemberResponse();
        response.membershipId = membership.getMembershipId();
        response.userId = membership.getUserId();
        response.role = membership.getRole() == null
                ? null
                : membership.getRole().toUpperCase();
        response.joinedAt = membership.getJoinedAt();
        if (user != null) {
            response.email = user.getEmail();
            response.fullName = user.getFullName();
            response.pending = user.isMustResetPassword();
        }
        return response;
    }

    // -- getters / setters ---------------------------------------------------

    public Integer getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(Integer membershipId) {
        this.membershipId = membershipId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public boolean isPending() {
        return pending;
    }

    public void setPending(boolean pending) {
        this.pending = pending;
    }
}
