package com.devpulse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /projects/{id}/invite}.
 *
 * <p>Distinct from the older {@link ProjectInviteRequest}, which takes a
 * {@code userId} and so cannot invite anyone who has not already registered.
 */
public class InviteByEmailRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 320, message = "Email must be at most 320 characters")
    private String email;

    @NotBlank(message = "role is required")
    @Pattern(regexp = "(?i)MANAGER|DEVELOPER",
            message = "role must be MANAGER or DEVELOPER")
    private String role;

    // -- constructors --------------------------------------------------------

    public InviteByEmailRequest() {
    }

    public InviteByEmailRequest(String email, String role) {
        this.email = email;
        this.role = role;
    }

    // -- getters / setters ---------------------------------------------------

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
