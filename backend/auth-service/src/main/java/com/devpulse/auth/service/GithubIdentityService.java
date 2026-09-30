package com.devpulse.auth.service;

import com.devpulse.auth.dto.GithubEmailInfo;
import com.devpulse.auth.dto.GithubOAuthTokenResponse;
import com.devpulse.auth.dto.GithubPreviewResponse;
import com.devpulse.auth.dto.GithubStatusResponse;
import com.devpulse.auth.dto.GithubUserInfo;
import com.devpulse.auth.dto.LinkGithubResponse;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ConflictException;
import com.devpulse.auth.exception.ExternalServiceException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.repository.UserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Links a DevPulse user to their GitHub account by saving its numeric id in
 * {@code users.github_id} and username in {@code users.github_username}.
 */
@Service
public class GithubIdentityService {

    private static final Logger log = LoggerFactory.getLogger(GithubIdentityService.class);

    private final UserRepository userRepository;
    private final GithubUserLookup githubUserLookup;
    private final RestClient restClient;

    @Value("${github.client-id:}")
    private String clientId;

    @Value("${github.client-secret:}")
    private String clientSecret;

    @Value("${github.redirect-uri:http://localhost:3000/auth/github/callback}")
    private String redirectUri;

    @Autowired
    public GithubIdentityService(UserRepository userRepository, GithubUserLookup githubUserLookup) {
        this(userRepository, githubUserLookup, null);
    }

    public GithubIdentityService(UserRepository userRepository, GithubUserLookup githubUserLookup, RestClient restClient) {
        this.userRepository = userRepository;
        this.githubUserLookup = githubUserLookup;
        if (restClient != null) {
            this.restClient = restClient;
        } else {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
            factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
            this.restClient = RestClient.builder().requestFactory(factory).build();
        }
    }

    public String getConnectUrl(Integer userId) {
        String effectiveClientId = (clientId != null && !clientId.isBlank()) ? clientId : "devpulse-github-app-id";
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        return "https://github.com/login/oauth/authorize?client_id=" + effectiveClientId
                + "&redirect_uri=" + encodedRedirect
                + "&scope=read:user";
    }

    @Transactional(readOnly = true)
    public GithubStatusResponse getStatus(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        boolean connected = user.getGithubId() != null;
        return new GithubStatusResponse(connected, user.getGithubId(), user.getGithubUsername());
    }

    @Transactional
    public GithubStatusResponse disconnect(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setGithubId(null);
        user.setGithubUsername(null);
        userRepository.save(user);
        log.info("User {} disconnected GitHub account", userId);
        return new GithubStatusResponse(false, null, null);
    }

    @Transactional
    public GithubStatusResponse handleOAuthCallback(Integer userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        GithubUserInfo userInfo = exchangeCodeAndFetchUserInfo(code);
        Long githubId = userInfo.id();
        String githubUsername = userInfo.login();

        userRepository.findFirstByGithubId(githubId)
                .filter(owner -> !owner.getUserId().equals(userId))
                .ifPresent(owner -> {
                    throw new ConflictException(
                            "That GitHub account is already linked to another DevPulse user");
                });

        user.setGithubId(githubId);
        user.setGithubUsername(githubUsername);
        if (user.getAvatarUrl() == null && userInfo.avatarUrl() != null) {
            user.setAvatarUrl(userInfo.avatarUrl());
        }
        userRepository.save(user);

        log.info("User {} OAuth linked GitHub account {} (id {})", userId, githubUsername, githubId);
        return new GithubStatusResponse(true, githubId, githubUsername);
    }

    /**
     * Exchanges an OAuth authorization code for a GitHub access token, retrieves
     * the user profile from GitHub, and ensures an email is resolved.
     */
    public GithubUserInfo exchangeCodeAndFetchUserInfo(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Authorization code is required");
        }

        try {
            Map<String, String> tokenPayload = new HashMap<>();
            tokenPayload.put("client_id", clientId != null ? clientId : "");
            tokenPayload.put("client_secret", clientSecret != null ? clientSecret : "");
            tokenPayload.put("code", code.trim());
            if (redirectUri != null && !redirectUri.isBlank()) {
                tokenPayload.put("redirect_uri", redirectUri.trim());
            }

            GithubOAuthTokenResponse tokenResponse = restClient.post()
                    .uri("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .body(tokenPayload)
                    .retrieve()
                    .body(GithubOAuthTokenResponse.class);

            if (tokenResponse != null && tokenResponse.error() != null) {
                log.warn("GitHub OAuth token exchange failed: {} ({})",
                        tokenResponse.error(), tokenResponse.errorDescription());
                throw new ExternalServiceException(
                        "GitHub OAuth error: " + (tokenResponse.errorDescription() != null ? tokenResponse.errorDescription() : tokenResponse.error()),
                        null);
            }

            String accessToken = tokenResponse != null ? tokenResponse.accessToken() : null;
            if (accessToken == null || accessToken.isBlank()) {
                throw new ExternalServiceException("Could not obtain access token from GitHub", null);
            }

            GithubUserInfo userInfo = restClient.get()
                    .uri("https://api.github.com/user")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "devpulse-auth-service")
                    .retrieve()
                    .body(GithubUserInfo.class);

            if (userInfo == null || userInfo.id() == null) {
                throw new ExternalServiceException("Failed to retrieve profile from GitHub", null);
            }

            String email = userInfo.email();
            if (email == null || email.isBlank()) {
                email = fetchPrimaryEmail(accessToken);
            }
            if (email == null || email.isBlank()) {
                email = userInfo.login().toLowerCase() + "@users.noreply.github.com";
            }

            return new GithubUserInfo(
                    userInfo.id(),
                    userInfo.login(),
                    userInfo.name(),
                    email,
                    userInfo.avatarUrl()
            );
        } catch (RestClientException e) {
            throw new ExternalServiceException("Failed to communicate with GitHub during OAuth exchange", e);
        }
    }

    private String fetchPrimaryEmail(String accessToken) {
        try {
            GithubEmailInfo[] emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "devpulse-auth-service")
                    .retrieve()
                    .body(GithubEmailInfo[].class);

            if (emails != null && emails.length > 0) {
                for (GithubEmailInfo item : emails) {
                    if (item.primary() && item.email() != null && !item.email().isBlank()) {
                        return item.email().trim();
                    }
                }
                for (GithubEmailInfo item : emails) {
                    if (item.verified() && item.email() != null && !item.email().isBlank()) {
                        return item.email().trim();
                    }
                }
                return emails[0].email() != null ? emails[0].email().trim() : null;
            }
        } catch (Exception e) {
            log.warn("Could not retrieve emails from GitHub: {}", e.getMessage());
        }
        return null;
    }

    @Transactional(readOnly = true)
    public GithubPreviewResponse preview(Integer userId, String username) {
        GithubUserLookup.GithubAccount account = githubUserLookup.findByUsername(username.trim())
                .orElseThrow(() -> new ResourceNotFoundException("GitHub user", username));

        boolean linkedToAnotherUser = userRepository.findFirstByGithubId(account.id())
                .map(owner -> !owner.getUserId().equals(userId))
                .orElse(false);

        return new GithubPreviewResponse(account.id(), account.login(), account.name(),
                account.avatarUrl(), account.profileUrl(), linkedToAnotherUser);
    }

    @Transactional
    public LinkGithubResponse link(Integer userId, String username) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        GithubUserLookup.GithubAccount account = githubUserLookup.findByUsername(username.trim())
                .orElseThrow(() -> new ResourceNotFoundException("GitHub user", username));

        userRepository.findFirstByGithubId(account.id())
                .filter(owner -> !owner.getUserId().equals(userId))
                .ifPresent(owner -> {
                    throw new ConflictException(
                            "That GitHub account is already linked to another DevPulse user");
                });

        user.setGithubId(account.id());
        user.setGithubUsername(account.login());
        userRepository.save(user);
        log.info("User {} linked GitHub account {} (id {})", userId, account.login(), account.id());
        return new LinkGithubResponse(account.id(), account.login());
    }
}
