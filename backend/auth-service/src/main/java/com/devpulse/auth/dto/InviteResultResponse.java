package com.devpulse.auth.dto;

/**
 * Result of {@code POST /projects/{id}/invite}.
 *
 * <p>There is one success shape, {@code ADDED_EXISTING_USER}: the email belonged
 * to an account in this company, so that person was added straight to the
 * project. An admin may only invite someone who has already registered, so an
 * address with no account behind it is a 404, not a second kind of success.
 *
 * <p>{@code CREATED_USER} was an earlier second path: the invite pre-created a
 * placeholder {@code users} row flagged {@code must_reset_password}, so that
 * {@code project_invitations.user_id} (NOT NULL) had something to point at. That
 * made inviting a write to the users table, so inviting an address that already
 * had an account hit the UNIQUE constraint on {@code users.email} and failed the
 * whole request. This endpoint no longer touches users at all. The constant is
 * kept only so an older client deserialising it still links; nothing returns it.
 *
 * <p>This response deliberately carries <b>no credential</b>. An earlier revision
 * returned a {@code temporaryPassword} for the admin to relay by hand; that made
 * the invite a second way into the account and left a live credential the admin
 * also knew. Registration is the single path in.
 */
public class InviteResultResponse {

    public static final String ADDED_EXISTING_USER = "ADDED_EXISTING_USER";

    /** @deprecated the placeholder-user path this described no longer exists. */
    @Deprecated
    public static final String CREATED_USER = "CREATED_USER";

    private String status;
    private Integer userId;
    private String email;
    private String role;

    public InviteResultResponse() {
    }

    public static InviteResultResponse addedExisting(Integer userId, String email, String role) {
        InviteResultResponse response = new InviteResultResponse();
        response.status = ADDED_EXISTING_USER;
        response.userId = userId;
        response.email = email;
        response.role = role;
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
}
