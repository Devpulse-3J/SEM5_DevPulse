package com.devpulse.auth.controller;

import com.devpulse.auth.dto.LinkGithubRequest;
import com.devpulse.auth.dto.LinkGithubResponse;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.service.GithubIdentityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code PUT /auth/me/github} - link the caller's GitHub account (authenticated). */
@RestController
@RequestMapping("/auth/me")
public class GithubIdentityController {

    private final GithubIdentityService githubIdentityService;

    public GithubIdentityController(GithubIdentityService githubIdentityService) {
        this.githubIdentityService = githubIdentityService;
    }

    @PutMapping("/github")
    public ResponseEntity<LinkGithubResponse> link(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody LinkGithubRequest request) {
        return ResponseEntity.ok(
                githubIdentityService.link(user.getUserId(), request.getGithubUsername()));
    }
}
