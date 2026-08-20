package com.devpulse.auth.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * JPA Entity representing an invitation sent by an Organization Admin
 * to a prospective team member.
 */
@Entity
@Table(name = "organization_invitations")
public class OrganizationInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invitation_id")
    private Integer invitationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "role", nullable = false, length = 50)
    private String role = "member";

    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private User invitedBy;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "pending";

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public OrganizationInvitation() {}

    public OrganizationInvitation(Company company, String email, String role, String token, User invitedBy, OffsetDateTime expiresAt) {
        this.company = company;
        this.email = email;
        this.role = role != null ? role : "member";
        this.token = token;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.status = "pending";
        this.createdAt = OffsetDateTime.now();
    }

    public Integer getInvitationId() { return invitationId; }
    public void setInvitationId(Integer invitationId) { this.invitationId = invitationId; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public User getInvitedBy() { return invitedBy; }
    public void setInvitedBy(User invitedBy) { this.invitedBy = invitedBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
