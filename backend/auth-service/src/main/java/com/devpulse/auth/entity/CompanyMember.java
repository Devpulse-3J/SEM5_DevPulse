package com.devpulse.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/**
 * JPA entity mapped to the {@code company_members} table.
 * <p>
 * Records that a user can act in a given company, and with what role there.
 * A user may hold any number of {@code member} rows (many companies, many
 * projects) but at most one {@code admin} row company-wide — enforced by a
 * partial unique index in the schema, not here.
 */
@Entity
@Table(name = "company_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "company_id"}))
public class CompanyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "membership_id")
    private Integer membershipId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    public CompanyMember() {
    }

    public CompanyMember(Integer userId, Integer companyId, String role) {
        this.userId = userId;
        this.companyId = companyId;
        this.role = role;
        this.joinedAt = OffsetDateTime.now();
    }

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

    public Integer getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Integer companyId) {
        this.companyId = companyId;
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

    public SystemRole getRoleEnum() {
        return SystemRole.fromDbValue(this.role);
    }

    public void setRoleEnum(SystemRole role) {
        this.role = role.toDbValue();
    }
}
