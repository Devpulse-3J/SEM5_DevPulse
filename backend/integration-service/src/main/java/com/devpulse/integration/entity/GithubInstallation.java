package com.devpulse.integration.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** A GitHub App installation and the company that claimed it. */
@Entity
@Table(name = "github_installations")
public class GithubInstallation {

    @Id
    @Column(name = "installation_id")
    private Long installationId;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "account_login", length = 255)
    private String accountLogin;

    @Column(name = "account_type", length = 50)
    private String accountType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public GithubInstallation() {}

    public GithubInstallation(Long installationId, Integer companyId) {
        this.installationId = installationId;
        this.companyId = companyId;
    }

    public Long getInstallationId() { return installationId; }
    public void setInstallationId(Long installationId) { this.installationId = installationId; }

    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }

    public String getAccountLogin() { return accountLogin; }
    public void setAccountLogin(String accountLogin) { this.accountLogin = accountLogin; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
