package com.devpulse.auth.controller;

import com.devpulse.auth.dto.GithubPreviewResponse;
import com.devpulse.auth.dto.LinkGithubRequest;
import com.devpulse.auth.dto.LinkGithubResponse;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.service.GithubIdentityService;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code PUT /auth/me/github} - link the caller's GitHub account (authenticated). */
@RestController
@RequestMapping("/auth/me")
public class GithubIdentityController {

    private static final Pattern GITHUB_USERNAME = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$");

    private final GithubIdentityService githubIdentityService;

    public GithubIdentityController(GithubIdentityService githubIdentityService) {
        this.githubIdentityService = githubIdentityService;
    }

    /** {@code GET /auth/me/github/lookup?username=} - who this username is; saves nothing. */
    @GetMapping("/github/lookup")
    public ResponseEntity<GithubPreviewResponse> lookup(
            @AuthenticationPrincipal User user,
            @RequestParam String username) {
        // Same rule as LinkGithubRequest. Checked by hand rather than with
        // @Validated: a ConstraintViolationException would come out as a 500
        // through this service's handler, and an invalid name is a 400.
        if (username == null || !GITHUB_USERNAME.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException("Not a valid GitHub username");
        }
        return ResponseEntity.ok(githubIdentityService.preview(user.getUserId(), username));
    }

    @PutMapping("/github")
    public ResponseEntity<LinkGithubResponse> link(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody LinkGithubRequest request) {
        return ResponseEntity.ok(
                githubIdentityService.link(user.getUserId(), request.getGithubUsername()));
    }
}
