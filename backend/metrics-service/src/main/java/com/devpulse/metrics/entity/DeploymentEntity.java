package com.devpulse.metrics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "deployments")
public class DeploymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deployment_id")
    private Integer id;

    @Column(name = "github_deployment_id")
    private Long githubDeploymentId;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "project_id")
    private Integer projectId;

    @Column(name = "commit_sha", length = 40)
    private String commitSha;

    @Column(nullable = false)
    private String environment = "production";

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "deployed_at", nullable = false)
    private Instant deployedAt;

    @Column(name = "failure_recovered_at")
    private Instant failureRecoveredAt;

    public DeploymentEntity() {
    }

    public Integer getId() { return id; }
    public String getStatus() { return status; }
    public Instant getDeployedAt() { return deployedAt; }
    public Instant getFailureRecoveredAt() { return failureRecoveredAt; }
    public void setGithubDeploymentId(Long githubDeploymentId) { this.githubDeploymentId = githubDeploymentId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }
    public void setProjectId(Integer projectId) { this.projectId = projectId; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public void setStatus(String status) { this.status = status; }
    public void setDeployedAt(Instant deployedAt) { this.deployedAt = deployedAt; }
    public void setFailureRecoveredAt(Instant failureRecoveredAt) { this.failureRecoveredAt = failureRecoveredAt; }
}
