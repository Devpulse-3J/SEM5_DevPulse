package com.devpulse.auth.dto;

/**
 * Who a GitHub username resolves to, shown so the user can confirm "that's me"
 * before anything is saved.
 */
public class GithubPreviewResponse {

    private Long githubId;
    private String githubLogin;
    private String name;
    private String avatarUrl;
    private String profileUrl;
    /** True when a different DevPulse user already has this GitHub account linked. */
    private boolean linkedToAnotherUser;

    public GithubPreviewResponse() {
    }

    public GithubPreviewResponse(Long githubId, String githubLogin, String name, String avatarUrl,
                                 String profileUrl, boolean linkedToAnotherUser) {
        this.githubId = githubId;
        this.githubLogin = githubLogin;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.profileUrl = profileUrl;
        this.linkedToAnotherUser = linkedToAnotherUser;
    }

    public Long getGithubId() { return githubId; }
    public void setGithubId(Long githubId) { this.githubId = githubId; }
    public String getGithubLogin() { return githubLogin; }
    public void setGithubLogin(String githubLogin) { this.githubLogin = githubLogin; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getProfileUrl() { return profileUrl; }
    public void setProfileUrl(String profileUrl) { this.profileUrl = profileUrl; }
    public boolean isLinkedToAnotherUser() { return linkedToAnotherUser; }
    public void setLinkedToAnotherUser(boolean linkedToAnotherUser) { this.linkedToAnotherUser = linkedToAnotherUser; }
}
