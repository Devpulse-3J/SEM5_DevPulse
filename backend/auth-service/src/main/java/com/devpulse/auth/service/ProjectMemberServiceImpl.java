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
import com.devpulse.auth.entity.OrganizationInvitation;
import com.devpulse.auth.repository.OrganizationInvitationRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.UserRepository;
import com.devpulse.auth.security.ProjectAccessService;
import com.devpulse.auth.security.RequestContext;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
    private final OrganizationInvitationRepository orgInviteRepository;
    private final JavaMailSender mailSender;

    public ProjectMemberServiceImpl(ProjectMemberRepository projectMemberRepository,
                                    UserRepository userRepository,
                                    ProjectAccessService projectAccessService,
                                    OrganizationInvitationRepository orgInviteRepository,
                                    @Autowired(required = false) JavaMailSender mailSender) {
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
        this.orgInviteRepository = orgInviteRepository;
        this.mailSender = mailSender;
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

            // If individual user has no company yet, assign them to this company
            if (target.getCompany() == null) {
                target.setCompany(admin.getCompany());
                userRepository.save(target);
            } else if (!target.getCompany().getCompanyId().equals(context.companyId())) {
                throw new ConflictException(
                        "That email is already registered to a different company workspace");
            }

            attachToProject(projectId, target, role);
            log.info("Admin {} invited user {} to project {} as {}",
                    admin.getEmail(), email, projectId, role);

            // Send notification email to existing user
            String companyName = admin.getCompany() != null ? admin.getCompany().getCompanyName() : "DevPulse Workspace";
            sendEmail(target.getEmail(),
                    "You have been added to " + companyName + " on DevPulse",
                    "Hello " + target.getFullName() + ",\n\n" +
                    "You have been added to the workspace \"" + companyName + "\" as a " + role.name() + "!\n\n" +
                    "Log in to DevPulse to start collaborating:\nhttp://localhost:3000/login\n");

            return InviteResultResponse.addedExisting(target.getUserId(), email, role.name());
        }

        // Unregistered email -> create organization invitation row in DB
        String token = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(7);
        OrganizationInvitation invitation = new OrganizationInvitation(
                admin.getCompany(), email, role.name(), token, admin, expiresAt);
        orgInviteRepository.save(invitation);

        log.info("Admin {} created OrganizationInvitation entity for unregistered address {} for project {}",
                admin.getEmail(), email, projectId);

        // Send invitation email to unregistered user
        String companyName = admin.getCompany() != null ? admin.getCompany().getCompanyName() : "DevPulse Workspace";
        sendEmail(email,
                "Invitation to join " + companyName + " on DevPulse",
                "Hello,\n\n" +
                "You have been invited to join the workspace \"" + companyName + "\" on DevPulse as a " + role.name() + "!\n\n" +
                "Please click the link below to create your account and accept your workspace invitation:\n" +
                "http://localhost:3000/accept-invite?token=" + token + "\n\n" +
                "(This invitation link will expire in 7 days).\n");

        return InviteResultResponse.invitedNew(email, role.name());
    }

    private void sendEmail(String toEmail, String subject, String body) {
        if (mailSender == null) {
            log.info("JavaMailSender not configured; skipping email dispatch to {}", toEmail);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("jayasuriyaumaya@gmail.com");
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email successfully sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
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
