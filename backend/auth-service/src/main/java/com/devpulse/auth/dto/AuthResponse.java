package com.devpulse.auth.dto;

/**
 * Response body returned after successful registration or login.
 * Contains the JWT access token and basic user information.
 */
public class AuthResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private Integer userId;
    private String email;
    private String fullName;
    private String systemRole;

    // -- constructors --------------------------------------------------------

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, long expiresIn, Integer userId,
                         String email, String fullName, String systemRole) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.systemRole = systemRole;
    }

    // -- getters / setters ---------------------------------------------------

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
    }
}
