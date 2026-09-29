package com.devpulse.integration.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A company's Jira Cloud OAuth connection (access/refresh token, connected
 * site). One row per company — reconnecting overwrites it.
 */
@Entity
@Table(name = "jira_connections", uniqueConstraints = {
    @UniqueConstraint(name = "uq_jira_connections_company", columnNames = { "company_id" })
})
public class JiraConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "connection_id")
    private Integer connectionId;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "cloud_id", nullable = false, length = 128)
    private String cloudId;

    @Column(name = "site_url", length = 500)
    private String siteUrl;

    @Column(name = "site_name", length = 255)
    private String siteName;

    @Column(name = "access_token", nullable = false, columnDefinition = "text")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "text")
    private String refreshToken;

    @Column(name = "connected_by_user_id")
    private Integer connectedByUserId;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Integer getConnectionId() { return connectionId; }
    public void setConnectionId(Integer connectionId) { this.connectionId = connectionId; }

    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }

    public String getCloudId() { return cloudId; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }

    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Integer getConnectedByUserId() { return connectedByUserId; }
    public void setConnectedByUserId(Integer connectedByUserId) { this.connectedByUserId = connectedByUserId; }

    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
