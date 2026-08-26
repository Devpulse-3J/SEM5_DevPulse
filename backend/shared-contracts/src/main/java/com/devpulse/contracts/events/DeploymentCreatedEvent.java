package com.devpulse.contracts.events;

import java.time.Instant;

public class DeploymentCreatedEvent extends BaseEvent {
    private Integer deploymentId;
    private String commitSha;
    private String environment;
    private String status;
    private Instant deployedAt;

    public DeploymentCreatedEvent() {
        super();
    }

    public DeploymentCreatedEvent(String eventId, Integer companyId, Integer projectId, Instant timestamp,
                                  Integer deploymentId, String commitSha, String environment,
                                  String status, Instant deployedAt) {
        super(eventId, companyId, projectId, "deployment.created", timestamp);
        this.deploymentId = deploymentId;
        this.commitSha = commitSha;
        this.environment = environment;
        this.status = status;
        this.deployedAt = deployedAt;
    }

    public Integer getDeploymentId() { return deploymentId; }
    public void setDeploymentId(Integer deploymentId) { this.deploymentId = deploymentId; }

    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getDeployedAt() { return deployedAt; }
    public void setDeployedAt(Instant deployedAt) { this.deployedAt = deployedAt; }
}
