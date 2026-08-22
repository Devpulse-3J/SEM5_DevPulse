package com.devpulse.auth.service;

import com.devpulse.auth.dto.AddProjectMemberRequest;
import com.devpulse.auth.dto.ChangeMemberRoleRequest;
import com.devpulse.auth.dto.InviteByEmailRequest;
import com.devpulse.auth.dto.InviteResultResponse;
import com.devpulse.auth.dto.ProjectMemberResponse;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.ProjectRole;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ConflictException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.UserRepository;
import com.devpulse.auth.security.ProjectAccessService;
import com.devpulse.auth.security.RequestContext;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private static final Logger log = LoggerFactory.getLogger(ProjectMemberServiceImpl.class);

    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;

    public ProjectMemberServiceImpl(ProjectMemberRepository projectMemberRepository,
                                    UserRepository userRepository,
                                    ProjectAccessService projectAccessService) {
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
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
        // Case-insensitive: an account stored as 'A@x.com' is the same person as
        // the 'a@x.com' being invited, and must not be treated as unregistered.
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);

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

        // Nobody has registered this address, and an invite may only name someone
        // who already has an account. This method must never write to users:
        // pre-creating a placeholder here is what used to collide with the UNIQUE
        // constraint on users.email and fail the whole invite.
        log.info("Admin {} tried to invite unregistered address {} to project {}",
                admin.getEmail(), email, projectId);
        throw new ResourceNotFoundException("User", email);
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
}
