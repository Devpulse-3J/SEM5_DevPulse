package com.devpulse.auth.service;

import com.devpulse.auth.dto.*;
import com.devpulse.auth.entity.*;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Service
public class WorkspaceInviteServiceImpl implements WorkspaceInviteService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInviteServiceImpl.class);

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final OrganizationInvitationRepository orgInviteRepository;
    private final WorkspaceJoinRequestRepository joinRequestRepository;
    private final ProjectInvitationRepository projectInviteRepository;
    private final JavaMailSender mailSender;

    public WorkspaceInviteServiceImpl(
            CompanyRepository companyRepository,
            UserRepository userRepository,
            ProjectMemberRepository projectMemberRepository,
            OrganizationInvitationRepository orgInviteRepository,
            WorkspaceJoinRequestRepository joinRequestRepository,
            ProjectInvitationRepository projectInviteRepository,
            @Autowired(required = false) JavaMailSender mailSender) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.orgInviteRepository = orgInviteRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.projectInviteRepository = projectInviteRepository;
        this.mailSender = mailSender;
    }

    @Override
    @Transactional
    public OrganizationInvitation inviteToOrganization(User actor, OrganizationInviteRequest request) {
        validateCompanyAdminAccess(actor, request.getCompanyId());

        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", request.getCompanyId()));

        String token = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(7);

        OrganizationInvitation invitation = new OrganizationInvitation(
                company, request.getEmail(), request.getRole(), token, actor, expiresAt);

        log.info("Admin {} created OrganizationInvitation for email {} in company {}", actor.getEmail(),
                request.getEmail(), company.getCompanyId());

        OrganizationInvitation saved = orgInviteRepository.save(invitation);

        // Send email invitation
        sendInviteEmail(saved.getEmail(), saved.getToken(), company.getCompanyName(), saved.getRole());

        return saved;
    }

    private void sendInviteEmail(String toEmail, String token, String companyName, String role) {
        if (mailSender == null) {
            log.info("JavaMailSender not configured; skipping email dispatch to {}", toEmail);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("jayasuriyaumaya@gmail.com");
            message.setTo(toEmail);
            message.setSubject("Invitation to join " + companyName + " on DevPulse");
            message.setText("Hello,\n\n" +
                    "You have been invited to join the workspace \"" + companyName + "\" on DevPulse as a " + role + "!\n\n" +
                    "Please click the link below to create your account and accept your workspace invitation:\n" +
                    "http://localhost:3000/accept-invite?token=" + token + "\n\n" +
                    "(This invitation link will expire in 7 days).\n");
            mailSender.send(message);
            log.info("Invitation email successfully sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Override
    @Transactional
    public User acceptOrganizationInvite(String token, User user) {
        OrganizationInvitation invitation = orgInviteRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", token));

        if ("accepted".equalsIgnoreCase(invitation.getStatus())) {
            throw new IllegalArgumentException("Invitation token has already been accepted");
        }

        if (invitation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            invitation.setStatus("expired");
            orgInviteRepository.save(invitation);
            throw new IllegalArgumentException("Invitation token has expired");
        }

        user.setCompany(invitation.getCompany());
        user.setSystemRole(invitation.getRole());
        User updatedUser = userRepository.save(user);

        invitation.setStatus("accepted");
        orgInviteRepository.save(invitation);

        log.info("User {} successfully accepted organization invitation to join company {}", user.getEmail(),
                invitation.getCompany().getCompanyId());
        return updatedUser;
    }

    @Override
    @Transactional
    public ProjectMember inviteToProject(User actor, Integer projectId, ProjectInviteRequest request) {
        // Must be Organization Admin or Project Manager
        boolean isAdmin = "admin".equalsIgnoreCase(actor.getSystemRole());
        boolean isManager = projectMemberRepository.findByUserId(actor.getUserId()).stream()
                .anyMatch(pm -> pm.getProjectId().equals(projectId) && "manager".equalsIgnoreCase(pm.getRole()));

        if (!isAdmin && !isManager) {
            throw new AccessDeniedException(
                    "Only Project Managers or Organization Admins can invite team members to a project");
        }

        User targetUser = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));

        ProjectMember membership = projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.getUserId())
                .orElseGet(() -> new ProjectMember(projectId, targetUser.getUserId(), request.getRole()));

        membership.setRole(request.getRole());
        log.info("User {} invited target user {} to project {} as {}", actor.getEmail(), targetUser.getEmail(),
                projectId, request.getRole());
        return projectMemberRepository.save(membership);
    }

    @Override
    @Transactional
    public WorkspaceJoinRequest requestToJoinWorkspace(User actor, Integer companyId, JoinWorkspaceRequestDto request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        WorkspaceJoinRequest joinRequest = joinRequestRepository
                .findByCompanyCompanyIdAndUserUserId(companyId, actor.getUserId())
                .orElseGet(() -> new WorkspaceJoinRequest(company, actor, request.getGithubUsername(),
                        request.getMessage()));

        joinRequest.setStatus("pending");
        joinRequest.setGithubUsername(request.getGithubUsername());
        joinRequest.setMessage(request.getMessage());

        log.info("User {} submitted join request to workspace company {}", actor.getEmail(), companyId);
        return joinRequestRepository.save(joinRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceJoinRequest> getPendingJoinRequests(User actor, Integer companyId) {
        validateCompanyAdminAccess(actor, companyId);
        return joinRequestRepository.findByCompanyCompanyIdAndStatus(companyId, "pending");
    }

    @Override
    @Transactional
    public WorkspaceJoinRequest approveJoinRequest(User actor, Integer companyId, Integer requestId) {
        validateCompanyAdminAccess(actor, companyId);

        WorkspaceJoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("JoinRequest", requestId));

        joinRequest.setStatus("approved");
        joinRequest.setReviewedBy(actor);
        joinRequest.setReviewedAt(OffsetDateTime.now());

        // Update target user's company membership
        User targetUser = joinRequest.getUser();
        targetUser.setCompany(joinRequest.getCompany());
        userRepository.save(targetUser);

        log.info("Admin {} approved join request ID {} for user {}", actor.getEmail(), requestId,
                targetUser.getEmail());
        return joinRequestRepository.save(joinRequest);
    }

    @Override
    @Transactional
    public WorkspaceJoinRequest rejectJoinRequest(User actor, Integer companyId, Integer requestId) {
        validateCompanyAdminAccess(actor, companyId);

        WorkspaceJoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("JoinRequest", requestId));

        joinRequest.setStatus("rejected");
        joinRequest.setReviewedBy(actor);
        joinRequest.setReviewedAt(OffsetDateTime.now());

        log.info("Admin {} rejected join request ID {} for user {}", actor.getEmail(), requestId,
                joinRequest.getUser().getEmail());
        return joinRequestRepository.save(joinRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> searchWorkspaces(String query) {
        if (query == null || query.isBlank()) {
            return companyRepository.findAll();
        }
        return companyRepository.findByCompanyNameContainingIgnoreCase(query.trim());
    }

    private void validateCompanyAdminAccess(User actor, Integer companyId) {
        if (actor.getCompany() == null || !actor.getCompany().getCompanyId().equals(companyId)) {
            throw new AccessDeniedException("User does not belong to the target organization");
        }
        if (!"admin".equalsIgnoreCase(actor.getSystemRole())) {
            throw new AccessDeniedException("Only Organization Admins can perform this action");
        }
    }
}
