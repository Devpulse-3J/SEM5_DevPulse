package com.devpulse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OrganizationInviteRequest {

    @NotNull(message = "companyId is required")
    private Integer companyId;

    @NotBlank(message = "email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    private String role = "member";

    public OrganizationInviteRequest() {}

    public OrganizationInviteRequest(Integer companyId, String email, String role) {
        this.companyId = companyId;
        this.email = email;
        this.role = role != null ? role : "member";
    }

    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
