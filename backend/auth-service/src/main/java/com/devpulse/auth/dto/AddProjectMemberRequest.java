package com.devpulse.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /projects/{id}/members} — adds a user who
 * already has an account in the company.
 *
 * <p>Carries {@code userId} as well as {@code role}: the path names the
 * project, so without it the request would not say who to add. To add someone
 * by email instead, use {@code POST /projects/{id}/invite}.
 *
 * <p>{@code role} is accepted in the API's uppercase form and converted to the
 * database's lowercase CHECK values by {@code ProjectRole.toDbValue()}.
 */
public class AddProjectMemberRequest {

    @NotNull(message = "userId is required")
    private Integer userId;

    @NotBlank(message = "role is required")
    @Pattern(regexp = "(?i)MANAGER|DEVELOPER",
            message = "role must be MANAGER or DEVELOPER")
    private String role;

    // -- constructors --------------------------------------------------------

    public AddProjectMemberRequest() {
    }

    public AddProjectMemberRequest(Integer userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    // -- getters / setters ---------------------------------------------------

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
}
