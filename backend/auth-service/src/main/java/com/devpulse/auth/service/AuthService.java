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

    /**
     * Same as {@link #getUserProfile(Integer)}, but scoped to the company the
     * caller's token names ({@code X-Company-Id}) when they belong to it, so
     * {@code companyId}, {@code companyName} and {@code systemRole} describe
     * the company they are acting in rather than always their home one. Falls
     * back to the home company when {@code activeCompanyId} is not one they
     * belong to. Also lists every company they belong to and, per project,
     * which company it is in.
     */
    UserProfileResponse getUserProfile(Integer userId, Integer activeCompanyId);

    /**
     * Issues a new token scoped to {@code targetCompanyId} instead of the
     * caller's home company, provided a {@code company_members} row records
     * that they belong there. Lets a non-admin who has been added to more
     * than one company act in whichever one this token names; their home
     * company (users.company_id) is untouched.
     *
     * @throws com.devpulse.auth.exception.ForbiddenException if the caller has
     *         no recorded membership in that company
     */
    AuthResponse switchCompany(Integer userId, Integer targetCompanyId);
}
