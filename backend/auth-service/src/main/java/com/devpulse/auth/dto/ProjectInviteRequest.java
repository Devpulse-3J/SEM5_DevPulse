package com.devpulse.auth.dto;

import jakarta.validation.constraints.NotNull;

public class ProjectInviteRequest {

    @NotNull(message = "userId is required")
    private Integer userId;

    private String role = "developer";

    public ProjectInviteRequest() {}

    public ProjectInviteRequest(Integer userId, String role) {
        this.userId = userId;
        this.role = role != null ? role : "developer";
    }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
