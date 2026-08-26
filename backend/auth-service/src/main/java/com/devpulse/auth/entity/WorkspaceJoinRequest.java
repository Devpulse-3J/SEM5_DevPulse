package com.devpulse.auth.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * JPA Entity representing a request by a user / GitHub user to join an Organization workspace,
 * awaiting Organization Admin approval.
 */
@Entity
@Table(name = "workspace_join_requests", uniqueConstraints = {
    @UniqueConstraint(name = "uq_workspace_join_requests_user_company", columnNames = {"user_id", "company_id"})
})
public class WorkspaceJoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Integer requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "github_username", length = 255)
    private String githubUsername;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "pending";

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    public WorkspaceJoinRequest() {}

    public WorkspaceJoinRequest(Company company, User user, String githubUsername, String message) {
        this.company = company;
        this.user = user;
        this.githubUsername = githubUsername;
        this.message = message;
        this.status = "pending";
        this.requestedAt = OffsetDateTime.now();
    }

    public Integer getRequestId() { return requestId; }
    public void setRequestId(Integer requestId) { this.requestId = requestId; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getGithubUsername() { return githubUsername; }
    public void setGithubUsername(String githubUsername) { this.githubUsername = githubUsername; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }

    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }

    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
