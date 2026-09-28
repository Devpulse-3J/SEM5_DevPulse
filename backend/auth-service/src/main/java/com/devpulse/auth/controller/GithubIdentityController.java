package com.devpulse.auth.controller;

import com.devpulse.auth.dto.GithubCallbackRequest;
import com.devpulse.auth.dto.GithubPreviewResponse;
import com.devpulse.auth.dto.GithubStatusResponse;
import com.devpulse.auth.dto.LinkGithubRequest;
import com.devpulse.auth.dto.LinkGithubResponse;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.service.GithubIdentityService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller for GitHub identity link and OAuth endpoints under {@code /auth/me/github}. */
@RestController
@RequestMapping("/auth/me/github")
public class GithubIdentityController {

    private static final Pattern GITHUB_USERNAME = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$");

    private final GithubIdentityService githubIdentityService;

    @Value("${devpulse.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public GithubIdentityController(GithubIdentityService githubIdentityService) {
        this.githubIdentityService = githubIdentityService;
    }

    /** Returns the GitHub OAuth authorization URL to initiate 1-click connect. */
    @GetMapping("/connect")
    public ResponseEntity<Map<String, String>> getConnectUrl(@AuthenticationPrincipal User user) {
        String url = githubIdentityService.getConnectUrl(user.getUserId());
        return ResponseEntity.ok(Map.of("url", url));
    }

    /** Returns current GitHub connection status for authenticated user. */
    @GetMapping("/status")
    public ResponseEntity<GithubStatusResponse> getStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(githubIdentityService.getStatus(user.getUserId()));
    }

    /** Disconnects GitHub identity for current user. */
    @DeleteMapping
    public ResponseEntity<GithubStatusResponse> disconnect(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(githubIdentityService.disconnect(user.getUserId()));
    }

    /** Handles OAuth code exchange for JSON clients (frontend API call). */
    @PostMapping("/callback")
    public ResponseEntity<GithubStatusResponse> handleOAuthCallbackPost(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody GithubCallbackRequest request) {
        GithubStatusResponse response = githubIdentityService.handleOAuthCallback(user.getUserId(), request.getCode());
        return ResponseEntity.ok(response);
    }

    /** Handles OAuth callback GET redirect from GitHub directly. */
    @GetMapping("/callback")
    public void handleOAuthCallbackGet(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpServletResponse httpResponse) throws IOException {
        if (error != null || code == null || code.isBlank()) {
            String redirectUrl = frontendBaseUrl + "/select-project?github=error&message=" +
                    URLEncoder.encode(error != null ? error : "GitHub authorization cancelled", StandardCharsets.UTF_8);
            httpResponse.sendRedirect(redirectUrl);
            return;
        }

        try {
            githubIdentityService.handleOAuthCallback(user.getUserId(), code);
            httpResponse.sendRedirect(frontendBaseUrl + "/select-project?github=connected");
        } catch (Exception e) {
            String redirectUrl = frontendBaseUrl + "/select-project?github=error&message=" +
                    URLEncoder.encode(e.getMessage() != null ? e.getMessage() : "Failed to link GitHub account", StandardCharsets.UTF_8);
            httpResponse.sendRedirect(redirectUrl);
        }
    }

    /** Legacy username lookup (preview). */
    @GetMapping("/lookup")
    public ResponseEntity<GithubPreviewResponse> lookup(
            @AuthenticationPrincipal User user,
            @RequestParam String username) {
        if (username == null || !GITHUB_USERNAME.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException("Not a valid GitHub username");
        }
        return ResponseEntity.ok(githubIdentityService.preview(user.getUserId(), username));
    }

    /** Legacy manual link by username. */
    @PutMapping
    public ResponseEntity<LinkGithubResponse> link(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody LinkGithubRequest request) {
        return ResponseEntity.ok(
                githubIdentityService.link(user.getUserId(), request.getGithubUsername()));
    }
}
