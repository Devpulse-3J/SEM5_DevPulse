package com.devpulse.auth.service;

import com.devpulse.auth.dto.AddProjectMemberRequest;
import com.devpulse.auth.dto.ChangeMemberRoleRequest;
import com.devpulse.auth.dto.InviteByEmailRequest;
import com.devpulse.auth.dto.InviteResultResponse;
import com.devpulse.auth.dto.ProjectMemberResponse;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.ProjectRole;
import com.devpulse.auth.entity.SystemRole;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ConflictException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.repository.CompanyRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.UserRepository;
import com.devpulse.auth.security.ProjectAccessService;
import com.devpulse.auth.security.RequestContext;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private static final Logger log = LoggerFactory.getLogger(ProjectMemberServiceImpl.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    /** Comfortably above the 8-character minimum the register endpoint enforces. */
    private static final int TEMP_PASSWORD_LENGTH = 16;

    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ProjectAccessService projectAccessService;
    private final PasswordEncoder passwordEncoder;

    public ProjectMemberServiceImpl(ProjectMemberRepository projectMemberRepository,
                                    UserRepository userRepository,
                                    CompanyRepository companyRepository,
                                    ProjectAccessService projectAccessService,
                                    PasswordEncoder passwordEncoder) {
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.projectAccessService = projectAccessService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(RequestContext context, Integer projectId) {
        projectAccessService.requireProjectVisible(context, projectId);

        List<ProjectMember> memberships = projectMemberRepository.findByProjectId(projectId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        // One query for every user on the project rather than one per row.
        Map<Integer, User> usersById = userRepository
                .findAllById(memberships.stream().map(ProjectMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        return memberships.stream()
                .sorted(Comparator.comparing(ProjectMember::getMembershipId))
                .map(membership -> ProjectMemberResponse.from(
                        membership, usersById.get(membership.getUserId())))
                .toList();
    }

    @Override
    @Transactional
    public ProjectMemberResponse addMember(RequestContext context, Integer projectId,
                                           AddProjectMemberRequest request) {
        User admin = projectAccessService.requireAdmin(context);
        projectAccessService.requireProjectInCompany(context, projectId);

        User target = requireUserInCompany(request.getUserId(), context.companyId());
        ProjectRole role = ProjectRole.valueOf(request.getRole().toUpperCase());

        if (projectMemberRepository.findByProjectIdAndUserId(projectId, target.getUserId())
                .isPresent()) {
            throw new ConflictException(
                    "User " + target.getEmail() + " is already a member of this project");
        }

        ProjectMember membership =
                new ProjectMember(projectId, target.getUserId(), role.toDbValue());
        ProjectMember saved = projectMemberRepository.save(membership);

        log.info("Admin {} added user {} to project {} as {}",
                admin.getEmail(), target.getEmail(), projectId, role);
        return ProjectMemberResponse.from(saved, target);
    }

    @Override
    @Transactional
    public ProjectMemberResponse changeRole(RequestContext context, Integer projectId,
                                            Integer userId, ChangeMemberRoleRequest request) {
        User admin = projectAccessService.requireAdmin(context);
        projectAccessService.requireProjectInCompany(context, projectId);

        User target = requireUserInCompany(userId, context.companyId());
        ProjectRole role = ProjectRole.valueOf(request.getRole().toUpperCase());

        ProjectMember membership = projectMemberRepository
                .findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project membership", userId));

        membership.setRole(role.toDbValue());
        ProjectMember saved = projectMemberRepository.save(membership);

        log.info("Admin {} changed user {} to {} on project {}",
                admin.getEmail(), target.getEmail(), role, projectId);
        return ProjectMemberResponse.from(saved, target);
    }

    @Override
    @Transactional
    public void removeMember(RequestContext context, Integer projectId, Integer userId) {
        User admin = projectAccessService.requireAdmin(context);
        projectAccessService.requireProjectInCompany(context, projectId);

        // Resolved through the project, so a membership id from another
        // project can never be removed by guessing.
        ProjectMember membership = projectMemberRepository
                .findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project membership", userId));

        projectMemberRepository.delete(membership);
        log.info("Admin {} removed user {} from project {}",
                admin.getEmail(), userId, projectId);
    }

    @Override
    @Transactional
    public InviteResultResponse inviteByEmail(RequestContext context, Integer projectId,
                                              InviteByEmailRequest request) {
        User admin = projectAccessService.requireAdmin(context);
        projectAccessService.requireProjectInCompany(context, projectId);

        String email = request.getEmail().trim().toLowerCase();
        ProjectRole role = ProjectRole.valueOf(request.getRole().toUpperCase());
        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            User target = existing.get();

            // users.email is UNIQUE across the whole table, not per company, so
            // an address registered to another tenant cannot be reused here and
            // cannot be silently adopted into this one either.
            if (target.getCompany() == null
                    || !target.getCompany().getCompanyId().equals(context.companyId())) {
                throw new ConflictException(
                        "That email is already registered to a different company");
            }

            attachToProject(projectId, target, role);
            log.info("Admin {} invited existing user {} to project {} as {}",
                    admin.getEmail(), email, projectId, role);
            return InviteResultResponse.addedExisting(target.getUserId(), email, role.name());
        }

        Company company = companyRepository.findById(context.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", context.companyId()));

        String temporaryPassword = generateTemporaryPassword();
        User created = new User();
        created.setEmail(email);
        // The real name arrives when the invitee completes their account; the
        // local part of the address is the only thing known now.
        created.setFullName(email.substring(0, email.indexOf('@')));
        created.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        created.setCompany(company);
        created.setSystemRoleEnum(SystemRole.MEMBER);
        created.setMustResetPassword(true);

        User saved = userRepository.save(created);
        attachToProject(projectId, saved, role);

        log.info("Admin {} invited new user {} to project {} as {} (account created, "
                        + "password reset required)",
                admin.getEmail(), email, projectId, role);
        return InviteResultResponse.createdUser(
                saved.getUserId(), email, role.name(), temporaryPassword);
    }

    // -- helpers -------------------------------------------------------------

    private void attachToProject(Integer projectId, User user, ProjectRole role) {
        ProjectMember membership = projectMemberRepository
                .findByProjectIdAndUserId(projectId, user.getUserId())
                .orElseGet(() -> new ProjectMember(projectId, user.getUserId(), role.toDbValue()));
        // Re-inviting someone already on the project updates their role rather
        // than failing — the unique constraint on (user_id, project_id) would
        // reject a second row anyway.
        membership.setRole(role.toDbValue());
        projectMemberRepository.save(membership);
    }

    private User requireUserInCompany(Integer userId, Integer companyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.getCompany() == null
                || !user.getCompany().getCompanyId().equals(companyId)) {
            // 404, not 403: confirming the id exists would leak across tenants.
            throw new ResourceNotFoundException("User", userId);
        }
        return user;
    }

    private String generateTemporaryPassword() {
        StringBuilder builder = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            builder.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return builder.toString();
    }
}
