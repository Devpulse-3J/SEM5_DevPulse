package com.devpulse.auth.service;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.dto.LoginRequest;
import com.devpulse.auth.dto.RegisterRequest;
import com.devpulse.auth.dto.UserProfileResponse;
import com.devpulse.auth.dto.UserProfileResponse.ProjectRoleEntry;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.SystemRole;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.repository.CompanyRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.UserRepository;
import com.devpulse.auth.security.JwtService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business-logic layer for authentication and user management.
 * <p>
 * Handles registration, login (credential verification + JWT issuance), and
 * user profile retrieval (including per-project role resolution).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       CompanyRepository companyRepository,
                       ProjectMemberRepository projectMemberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Registers a new user, hashes their password, saves them, and returns a
     * signed JWT so they are logged in immediately after registration.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check for duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "An account with this email already exists");
        }

        // Resolve the company
        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Company not found with ID: " + request.getCompanyId()));

        // Build and save the user entity
        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCompany(company);
        user.setSystemRoleEnum(SystemRole.MEMBER); // new users default to 'member'

        user = userRepository.save(user);

        // Generate JWT
        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                jwtService.getExpirationSeconds(),
                user.getUserId(),
                user.getEmail(),
                user.getFullName(),
                user.getSystemRole()
        );
    }

    /**
     * Authenticates a user by email + password and returns a signed JWT.
     */
    public AuthResponse login(LoginRequest request) {
        // Delegate credential verification to Spring Security's AuthenticationManager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // If authentication succeeds, load the user and generate a token
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(); // should not happen after successful auth

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                jwtService.getExpirationSeconds(),
                user.getUserId(),
                user.getEmail(),
                user.getFullName(),
                user.getSystemRole()
        );
    }

    /**
     * Returns the authenticated user's full profile, including their
     * company-level role and all per-project role memberships.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found with ID: " + userId));

        // Resolve all per-project memberships
        List<ProjectMember> memberships = projectMemberRepository.findByUserId(userId);
        List<ProjectRoleEntry> projectRoles = memberships.stream()
                .map(m -> new ProjectRoleEntry(m.getProjectId(), m.getRole()))
                .collect(Collectors.toList());

        UserProfileResponse profile = new UserProfileResponse();
        profile.setUserId(user.getUserId());
        profile.setEmail(user.getEmail());
        profile.setFullName(user.getFullName());
        profile.setSystemRole(user.getSystemRole());
        profile.setCompanyId(user.getCompany().getCompanyId());
        profile.setCompanyName(user.getCompany().getCompanyName());
        profile.setProjectRoles(projectRoles);

        return profile;
    }
}
