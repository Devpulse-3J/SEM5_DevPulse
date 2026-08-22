package com.devpulse.auth.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * JPA Entity representing a project-level membership invitation
 * created by a Project Manager.
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
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public User getInvitedBy() { return invitedBy; }
    public void setInvitedBy(User invitedBy) { this.invitedBy = invitedBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
