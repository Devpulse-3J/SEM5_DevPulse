package com.devpulse.auth.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * JPA Entity representing a project-level membership invitation
 * created by a company admin.
 *
 * <p>An invitation identifies its invitee one of two ways. Someone who already
 * has an account is added to the project immediately and never gets a row here.
 * An address with no account behind it gets a pending row keyed by
 * {@code email} with a one-time {@code token} and an expiry; {@code user}
 * stays null until the invitee registers or accepts, at which point the row is
 * turned into a {@code project_members} row and marked accepted.
 *
 * <p>The email, token, expiry and company columns come from
 * {@code V9__email_based_project_invitations.sql}; {@code user_id} is nullable
 * since that migration.
 */
@Entity
@Table(name = "project_invitations")
public class ProjectInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invitation_id")
    private Integer invitationId;

    @Column(name = "project_id", nullable = false)
    private Integer projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "token", length = 255)
    private String token;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "role", nullable = false, length = 50)
    private String role = "developer";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private User invitedBy;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "pending";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public ProjectInvitation() {}

    public ProjectInvitation(Integer projectId, User user, String role, User invitedBy) {
        this.projectId = projectId;
        this.user = user;
        this.role = role != null ? role : "developer";
        this.invitedBy = invitedBy;
        this.status = "pending";
        this.createdAt = OffsetDateTime.now();
    }

    public Integer getInvitationId() { return invitationId; }
    public void setInvitationId(Integer invitationId) { this.invitationId = invitationId; }

    public Integer getProjectId() { return projectId; }
    public void setProjectId(Integer projectId) { this.projectId = projectId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public User getInvitedBy() { return invitedBy; }
    public void setInvitedBy(User invitedBy) { this.invitedBy = invitedBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
