package com.devpulse.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code PUT /projects/{id}/members/{userId}}. The user is
 * named by the path, so only the new role travels in the body.
 */
public class ChangeMemberRoleRequest {

    @NotBlank(message = "role is required")
    @Pattern(regexp = "(?i)MANAGER|DEVELOPER",
            message = "role must be MANAGER or DEVELOPER")
    private String role;

    // -- constructors --------------------------------------------------------

    public ChangeMemberRoleRequest() {
    }

    public ChangeMemberRoleRequest(String role) {
        this.role = role;
    }

    // -- getters / setters ---------------------------------------------------

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
