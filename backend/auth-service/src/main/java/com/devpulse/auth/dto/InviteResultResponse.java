package com.devpulse.auth.dto;

/**
 * Result of {@code POST /projects/{id}/invite}.
 *
 * <p>{@code status} distinguishes the two MVP paths:
 * <ul>
 *   <li>{@code ADDED_EXISTING_USER} — the email already belonged to this
 *       company, so the person was added straight to the project.</li>
 *   <li>{@code CREATED_USER} — no account existed, so an unclaimed placeholder
 *       was created and {@code mustResetPassword} is true. The invitee takes
 *       ownership of it by registering with this email, which sets their own
 *       password on the same row and keeps the membership just granted.</li>
 * </ul>
 *
 * <p>This response deliberately carries <b>no credential</b>. An earlier revision
 * returned a {@code temporaryPassword} for the admin to relay by hand; that made
 * the invite a second way into the account, left a live credential the admin also
 * knew, and — because nothing ever cleared {@code must_reset_password} — kept the
 * account claimable by anyone even after its owner started using it. Registration
 * is now the single path in.
 *
 * <p>Still outstanding: nothing proves the registrant owns the invited address.
 * A tokenised email invitation ({@code project_invitations} exists from V5) is
 * the fix once a mail transport is available.
 */
public class InviteResultResponse {

    public static final String ADDED_EXISTING_USER = "ADDED_EXISTING_USER";
    public static final String CREATED_USER = "CREATED_USER";

    private String status;
    private Integer userId;
    private String email;
    private String role;
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

    public static InviteResultResponse createdUser(Integer userId, String email, String role) {
        InviteResultResponse response = new InviteResultResponse();
        response.status = CREATED_USER;
        response.userId = userId;
        response.email = email;
        response.role = role;
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

    public boolean isMustResetPassword() {
        return mustResetPassword;
    }

    public void setMustResetPassword(boolean mustResetPassword) {
        this.mustResetPassword = mustResetPassword;
    }
}
