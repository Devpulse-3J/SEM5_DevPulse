package com.devpulse.auth.controller;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.dto.LoginRequest;
import com.devpulse.auth.dto.RegisterRequest;
import com.devpulse.auth.dto.UserProfileResponse;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 * <p>
 * <ul>
 *   <li>{@code POST /auth/register} — create a new account (public)</li>
 *   <li>{@code POST /auth/login} — obtain a JWT (public)</li>
 *   <li>{@code GET  /auth/me} — retrieve the authenticated user's profile (requires JWT)</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user and returns a JWT so they are logged in immediately.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates a user by email + password and returns a JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the profile of the currently authenticated user, including their
     * system role and all per-project role memberships.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(
            @AuthenticationPrincipal User user,
            @RequestHeader(value = "X-Company-Id", required = false) String companyHeader) {
        // The gateway sets X-Company-Id from the token's companyId claim, so this
        // is the company the caller is currently acting in (it differs from their
        // home company after /auth/companies/{id}/switch). It is optional: a user
        // with no company has no claim, and must still get a profile.
        Integer activeCompanyId = parseCompanyId(companyHeader);
        UserProfileResponse profile = activeCompanyId == null
                ? authService.getUserProfile(user.getUserId())
                : authService.getUserProfile(user.getUserId(), activeCompanyId);
        return ResponseEntity.ok(profile);
    }

    private static Integer parseCompanyId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            int id = Integer.parseInt(header.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Issues a new token scoped to {@code companyId} instead of the caller's
     * home company, provided they have a recorded {@code company_members} row
     * there. Lets a non-admin who belongs to several companies act in
     * whichever one they switch to; their home company is unaffected.
     */
    @PostMapping("/companies/{companyId}/switch")
    public ResponseEntity<AuthResponse> switchCompany(
            @AuthenticationPrincipal User user,
            @PathVariable Integer companyId) {
        AuthResponse response = authService.switchCompany(user.getUserId(), companyId);
        return ResponseEntity.ok(response);
    }
}
