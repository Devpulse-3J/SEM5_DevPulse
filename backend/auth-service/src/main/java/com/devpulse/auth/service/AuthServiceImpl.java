package com.devpulse.auth.service;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.dto.LoginRequest;
import com.devpulse.auth.dto.RegisterRequest;
import com.devpulse.auth.dto.UserProfileResponse;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.SystemRole;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.DuplicateEmailException;
import com.devpulse.auth.exception.InvalidCredentialsException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.mapper.UserMapper;
import com.devpulse.auth.repository.CompanyRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.UserRepository;
import com.devpulse.auth.security.JwtService;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete implementation of the {@link AuthService} contract.
 * Coordinates domain repositories, security services, and entity mappers.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;

    public AuthServiceImpl(UserRepository userRepository,
                           CompanyRepository companyRepository,
                           ProjectMemberRepository projectMemberRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AuthenticationManager authenticationManager,
                           UserMapper userMapper) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        Company company;
        boolean isAdmin = Boolean.TRUE.equals(request.getIsCompany()) 
                || (request.getCompanyName() != null && !request.getCompanyName().isBlank());

        if (isAdmin) {
            // Registering as a Company -> create new Company and assign ADMIN role
            Company newCompany = new Company();
            String name = (request.getCompanyName() != null && !request.getCompanyName().isBlank()) 
                    ? request.getCompanyName().trim() 
                    : request.getFullName().trim() + "'s Organization";
            newCompany.setCompanyName(name);
            newCompany.setSubscriptionPlan("pro");
            newCompany.setCreatedAt(OffsetDateTime.now());
            company = companyRepository.save(newCompany);
        } else if (request.getCompanyId() != null) {
            // Joining an existing company by ID
            company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Company", request.getCompanyId()));
        } else {
            // Individual signup -> associate with default or personal workspace
            company = companyRepository.findById(1).orElseGet(() -> {
                Company defaultCompany = new Company(request.getFullName().trim() + "'s Workspace", "free");
                defaultCompany.setCreatedAt(OffsetDateTime.now());
                return companyRepository.save(defaultCompany);
            });
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCompany(company);
        user.setSystemRoleEnum(isAdmin ? SystemRole.ADMIN : SystemRole.MEMBER);
        user.setCreatedAt(OffsetDateTime.now());

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(savedUser);

        return userMapper.toAuthResponse(savedUser, token, jwtService.getExpirationSeconds());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException(ex.getMessage());
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getEmail()));

        String token = jwtService.generateToken(user);

        return userMapper.toAuthResponse(user, token, jwtService.getExpirationSeconds());
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<ProjectMember> memberships = projectMemberRepository.findByUserId(userId);

        return userMapper.toUserProfileResponse(user, memberships);
    }
}
