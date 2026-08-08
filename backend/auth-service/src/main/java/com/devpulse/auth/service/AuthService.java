package com.devpulse.auth.service;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.dto.LoginRequest;
import com.devpulse.auth.dto.RegisterRequest;
import com.devpulse.auth.dto.UserProfileResponse;

/**
 * Service interface defining authentication and user identity operations.
 * Adheres to the Interface Segregation and Dependency Inversion principles.
 */
public interface AuthService {

    /**
     * Registers a new user with the default member role and returns an authenticated response with JWT.
     *
     * @param request Registration details (email, password, fullName, companyId)
     * @return AuthResponse containing token and user details
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates a user with email and password and returns an authenticated response with JWT.
     *
     * @param request Login credentials (email, password)
     * @return AuthResponse containing token and user details
     */
    AuthResponse login(LoginRequest request);

    /**
     * Retrieves the profile and all per-project roles for the specified user.
     *
     * @param userId ID of the user
     * @return UserProfileResponse containing company info and per-project roles
     */
    UserProfileResponse getUserProfile(Integer userId);
}
