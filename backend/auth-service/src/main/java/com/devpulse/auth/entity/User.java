package com.devpulse.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * JPA entity mapped to the {@code users} table.
 * <p>
 * Implements {@link UserDetails} so Spring Security can authenticate against it
 * directly. The {@code system_role} column determines company-wide authority
 * (admin vs member); per-project roles are resolved separately via the
 * {@code project_members} table.
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "system_role", nullable = false, length = 50)
    private String systemRole;

    @Column(name = "github_id")
    private Long githubId;

    @Column(name = "jira_account_id", length = 128)
    private String jiraAccountId;

    /**
     * Set when the account was created by a project invitation rather than by
     * the person themselves — its password hash is random and unknown, so the
     * account cannot be used until the password is reset.
     */
    @Column(name = "must_reset_password", nullable = false)
    private boolean mustResetPassword;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    // -- constructors --------------------------------------------------------

    public User() {
    }

    // -- UserDetails implementation ------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Expose the system-level role as a Spring Security authority.
        // Example: ROLE_ADMIN or ROLE_MEMBER
        return List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.toUpperCase()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // -- getters / setters ---------------------------------------------------

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
    }

    public SystemRole getSystemRoleEnum() {
        return SystemRole.fromDbValue(this.systemRole);
    }

    public void setSystemRoleEnum(SystemRole role) {
        this.systemRole = role.toDbValue();
    }

    public Long getGithubId() {
        return githubId;
    }

    public void setGithubId(Long githubId) {
        this.githubId = githubId;
    }

    public String getJiraAccountId() {
        return jiraAccountId;
    }

    public void setJiraAccountId(String jiraAccountId) {
        this.jiraAccountId = jiraAccountId;
    }

    public boolean isMustResetPassword() {
        return mustResetPassword;
    }

    public void setMustResetPassword(boolean mustResetPassword) {
        this.mustResetPassword = mustResetPassword;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
