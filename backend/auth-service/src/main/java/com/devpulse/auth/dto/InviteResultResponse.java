package com.devpulse.auth.dto;

/**
 * Result of {@code POST /projects/{id}/invite}.
 *
 * <p>{@code status} distinguishes the two MVP paths:
 * <ul>
 *   <li>{@code ADDED_EXISTING_USER} — the email already belonged to this
 *       company, so the person was added straight to the project.</li>
 *   <li>{@code CREATED_USER} — no account existed, so a placeholder was created
 *       with a random password. {@code temporaryPassword} is populated in this
 *       case <b>only</b>.</li>
 * </ul>
 *
 * <p><b>Tech debt.</b> Returning a password in an API response is a stopgap:
 * there is no mail transport and no password-reset endpoint, so the admin
 * relaying it by hand is the only way the invitee can sign in. Replace this
 * with a tokenised email invitation ({@code project_invitations} already exists
 * from V5) and drop the field.
 */
public class InviteResultResponse {

    public static final String ADDED_EXISTING_USER = "ADDED_EXISTING_USER";
    public static final String CREATED_USER = "CREATED_USER";

    private String status;
    private Integer userId;
    private String email;
    private String role;
    private String temporaryPassword;
    private boolean mustResetPassword;

    public InviteResultResponse() {
    }

    public static InviteResultResponse addedExisting(Integer userId, String email, String role) {
        InviteResultResponse response = new InviteResultResponse();
        response.status = ADDED_EXISTING_USER;
        response.userId = userId;
        response.email = email;
        response.role = role;
        response.mustResetPassword = false;
        return response;
    }

    public static InviteResultResponse createdUser(Integer userId, String email, String role,
                                                   String temporaryPassword) {
        InviteResultResponse response = new InviteResultResponse();
        response.status = CREATED_USER;
        response.userId = userId;
        response.email = email;
        response.role = role;
        response.temporaryPassword = temporaryPassword;
        response.mustResetPassword = true;
        return response;
    }

    // -- getters / setters ---------------------------------------------------

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

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

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public void setTemporaryPassword(String temporaryPassword) {
        this.temporaryPassword = temporaryPassword;
    }

    public boolean isMustResetPassword() {
        return mustResetPassword;
    }

    public void setMustResetPassword(boolean mustResetPassword) {
        this.mustResetPassword = mustResetPassword;
    }
}
