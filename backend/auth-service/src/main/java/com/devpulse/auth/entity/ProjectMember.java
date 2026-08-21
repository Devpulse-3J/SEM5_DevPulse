package com.devpulse.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * JPA entity mapped to the {@code project_members} table.
 * <p>
 * Represents a user's membership in a specific project and their per-project
 * role (manager or developer). The same user may have different roles on
 * different projects.
 */
@Entity
@Table(name = "project_members")
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "membership_id")
    private Integer membershipId;

    @Column(name = "project_id", nullable = false)
    private Integer projectId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @Column(name = "joined_at", nullable = false, updatable = false,
            columnDefinition = "timestamptz")
    private OffsetDateTime joinedAt;

    // -- constructors --------------------------------------------------------

    public ProjectMember() {
    }

    public ProjectMember(Integer projectId, Integer userId, String role) {
        this.projectId = projectId;
        this.userId = userId;
        this.role = role != null ? role : "developer";
        this.joinedAt = OffsetDateTime.now();
    }

    // -- getters / setters ---------------------------------------------------

    public Integer getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(Integer membershipId) {
        this.membershipId = membershipId;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public ProjectRole getRoleEnum() {
        return ProjectRole.fromDbValue(this.role);
    }

    public void setRoleEnum(ProjectRole role) {
        this.role = role.toDbValue();
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
