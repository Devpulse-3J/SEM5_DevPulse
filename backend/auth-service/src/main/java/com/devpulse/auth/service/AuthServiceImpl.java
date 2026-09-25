package com.devpulse.auth.service;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.dto.LoginRequest;
import com.devpulse.auth.dto.RegisterRequest;
import com.devpulse.auth.dto.UserProfileResponse;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.CompanyMember;
import com.devpulse.auth.entity.Project;
import com.devpulse.auth.entity.ProjectInvitation;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.SystemRole;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.DuplicateEmailException;
import com.devpulse.auth.exception.ForbiddenException;
import com.devpulse.auth.exception.InvalidCredentialsException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.mapper.UserMapper;
import com.devpulse.auth.repository.CompanyMemberRepository;
import com.devpulse.auth.repository.CompanyRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.ProjectRepository;
import com.devpulse.auth.repository.UserRepository;
import com.devpulse.auth.security.JwtService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final CompanyMemberRepository companyMemberRepository;
    private final ProjectRepository projectRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final ProjectInvitationClaimService invitationClaimService;

    public AuthServiceImpl(UserRepository userRepository,
                           CompanyRepository companyRepository,
                           ProjectMemberRepository projectMemberRepository,
                           CompanyMemberRepository companyMemberRepository,
                           ProjectRepository projectRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AuthenticationManager authenticationManager,
                           UserMapper userMapper,
                           ProjectInvitationClaimService invitationClaimService) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.companyMemberRepository = companyMemberRepository;
        this.projectRepository = projectRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userMapper = userMapper;
        this.invitationClaimService = invitationClaimService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Legacy path. A project invite for an unknown email used to pre-create a
        // placeholder users row (V8__project_flow_enhancements.sql) so the
        // membership had something to point at, flagged must_reset_password with a
        // password hash nobody knows. Rejecting that row as a duplicate would lock
        // the invitee out of the account created for them, so registration CLAIMS it
        // instead: same user_id, so the memberships already attached survive.
        //
        // Nothing creates these rows any more: an invite for an unregistered address
        // is now a pending project_invitations row, claimed with its token below.
        // This path stays only until any pre-existing placeholders are gone.
        User invited = userRepository.findByEmail(request.getEmail())
                .filter(User::isMustResetPassword)
                .orElse(null);
        if (invited != null) {
            return claimInvitedAccount(invited, request);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        if (request.getInviteToken() != null && !request.getInviteToken().isBlank()) {
            return registerWithProjectInvitation(request);
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
            // Individual signup without company invite -> company is null until invited or joined
            company = null;
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCompany(company);
        user.setSystemRoleEnum(isAdmin ? SystemRole.ADMIN : SystemRole.MEMBER);
        user.setCreatedAt(OffsetDateTime.now());

        User savedUser = userRepository.save(user);

        // Mirrors users.company_id/system_role into company_members so the new
        // multi-company model already has this row; users.company_id stays the
        // source of truth for this user's home company and is untouched.
        if (company != null) {
            recordCompanyMembership(savedUser.getUserId(), company.getCompanyId(),
                    isAdmin ? SystemRole.ADMIN : SystemRole.MEMBER);
        }

        String token = jwtService.generateToken(savedUser);

        return userMapper.toAuthResponse(savedUser, token, jwtService.getExpirationSeconds());
    }

    /**
     * Dual-writes a {@code company_members} row alongside the legacy
     * {@code users.company_id}/{@code system_role} columns, without altering
     * either. Idempotent, so it can never conflict with a row that already
     * exists for this (user, company) pair.
     */
    private void recordCompanyMembership(Integer userId, Integer companyId, SystemRole role) {
        companyMemberRepository.findByUserIdAndCompanyId(userId, companyId)
                .orElseGet(() -> companyMemberRepository.save(
                        new CompanyMember(userId, companyId, role.toDbValue())));
    }

    /**
     * Registers someone who arrived through a project invitation email.
     * <p>
     * A bad, expired or mismatched token fails the registration outright rather
     * than falling back to a plain signup: that would silently strand the invitee
     * in an account with no company and no project, and the invite would look
     * accepted. Company and role come from the invitation, never the request, so
     * a token cannot be used to open a new company or escalate to admin.
     */
    private AuthResponse registerWithProjectInvitation(RegisterRequest request) {
        ProjectInvitation invitation = invitationClaimService
                .requirePendingInvitation(request.getInviteToken(), request.getEmail());
        Company company = invitationClaimService.requireInvitedCompany(invitation);

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCompany(company);
        user.setSystemRoleEnum(SystemRole.MEMBER);
        user.setCreatedAt(OffsetDateTime.now());

        User savedUser = userRepository.save(user);
        invitationClaimService.complete(invitation, savedUser);
        recordCompanyMembership(savedUser.getUserId(), company.getCompanyId(), SystemRole.MEMBER);

        String token = jwtService.generateToken(savedUser);
        return userMapper.toAuthResponse(savedUser, token, jwtService.getExpirationSeconds());
    }

    /**
     * Completes an invited-but-unclaimed account: the invitee sets their own name
     * and password on the row the invite created.
     * <p>
     * Company and system role are deliberately NOT taken from the request. The
     * placeholder already belongs to the company that invited it and carries that
     * company's project memberships; honouring a {@code companyName} here would move
     * the user to a new company and orphan those memberships — losing exactly the
     * access the invite was meant to grant. Keeping the role at what the inviter set
     * also stops a claim from silently escalating to admin.
     */
    private AuthResponse claimInvitedAccount(User invited, RegisterRequest request) {
        invited.setFullName(request.getFullName());
        invited.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        // The account now has a password its owner chose, so it is no longer a
        // placeholder and a second register() must be rejected as a duplicate.
        invited.setMustResetPassword(false);

        User claimed = userRepository.save(invited);
        String token = jwtService.generateToken(claimed);
        return userMapper.toAuthResponse(claimed, token, jwtService.getExpirationSeconds());
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
        return getUserProfile(userId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Integer userId, Integer activeCompanyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<ProjectMember> memberships = projectMemberRepository.findByUserId(userId);
        List<CompanyMember> companyMemberships = companyMemberRepository.findByUserId(userId);

        // Home company unless the token names another one the user belongs to.
        Company activeCompany = user.getCompany();
        String activeRole = user.getSystemRole();
        if (activeCompanyId != null) {
            for (CompanyMember membership : companyMemberships) {
                if (membership.getCompanyId().equals(activeCompanyId)) {
                    Company named = companyRepository.findById(activeCompanyId).orElse(null);
                    if (named != null) {
                        activeCompany = named;
                        activeRole = membership.getRole();
                    }
                    break;
                }
            }
        }

        Map<Integer, Project> projectsById = projectRepository
                .findAllById(memberships.stream().map(ProjectMember::getProjectId).toList())
                .stream()
                .collect(Collectors.toMap(Project::getProjectId, Function.identity()));

        Map<Integer, Company> companiesById = companyRepository
                .findAllById(companyMemberships.stream().map(CompanyMember::getCompanyId).toList())
                .stream()
                .collect(Collectors.toMap(Company::getCompanyId, Function.identity()));

        List<UserProfileResponse.ProjectRoleEntry> projectRoles = memberships.stream()
                .map(pm -> {
                    Project project = projectsById.get(pm.getProjectId());
                    Company owner = project != null ? project.getCompany() : null;
                    return new UserProfileResponse.ProjectRoleEntry(
                            pm.getProjectId(), pm.getRole(),
                            owner != null ? owner.getCompanyId() : null,
                            owner != null ? owner.getCompanyName() : null,
                            project != null ? project.getProjectName() : null);
                })
                .toList();

        List<UserProfileResponse.CompanyEntry> companies = companyMemberships.stream()
                .map(cm -> {
                    Company company = companiesById.get(cm.getCompanyId());
                    return new UserProfileResponse.CompanyEntry(
                            cm.getCompanyId(),
                            company != null ? company.getCompanyName() : null,
                            cm.getRole());
                })
                .toList();

        return userMapper.toUserProfileResponse(user, activeCompany, activeRole, projectRoles, companies);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse switchCompany(Integer userId, Integer targetCompanyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        CompanyMember membership = companyMemberRepository
                .findByUserIdAndCompanyId(userId, targetCompanyId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of that company"));

        String token = jwtService.generateToken(user, targetCompanyId, membership.getRole());
        return userMapper.toAuthResponse(user, token, jwtService.getExpirationSeconds(),
                targetCompanyId, membership.getRole());
    }
}
