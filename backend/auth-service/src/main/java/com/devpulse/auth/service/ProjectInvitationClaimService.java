package com.devpulse.auth.service;

import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.ProjectInvitation;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ConflictException;
import com.devpulse.auth.exception.ForbiddenException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.repository.CompanyRepository;
import com.devpulse.auth.repository.ProjectInvitationRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.UserRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a pending, email-addressed {@link ProjectInvitation} into a
 * {@code project_members} row.
 *
 * <p>The one-time token from the invitation email is what proves the person
 * holds that mailbox. Matching on the email address alone would let anyone who
 * registers an invited address first (accounts are not email-verified) take the
 * invitee's project role, so a claim always needs both the token and a matching
 * address.
 */
@Service
public class ProjectInvitationClaimService {

    private static final Logger log = LoggerFactory.getLogger(ProjectInvitationClaimService.class);

    private final ProjectInvitationRepository invitationRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public ProjectInvitationClaimService(ProjectInvitationRepository invitationRepository,
                                         ProjectMemberRepository projectMemberRepository,
                                         CompanyRepository companyRepository,
                                         UserRepository userRepository) {
        this.invitationRepository = invitationRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns the pending invitation for this token, provided it has not been
     * used or expired and was sent to {@code email}. Changes nothing.
     */
    @Transactional(readOnly = true)
    public ProjectInvitation requirePendingInvitation(String token, String email) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invitation token is required");
        }
        ProjectInvitation invitation = invitationRepository.findByToken(token.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "token"));

        if (!"pending".equalsIgnoreCase(invitation.getStatus())) {
            throw new IllegalArgumentException(
                    "This invitation has already been used or is no longer valid");
        }
        if (invitation.getExpiresAt() != null
                && invitation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException(
                    "This invitation has expired. Ask an admin to send a new one");
        }
        if (invitation.getEmail() == null || email == null
                || !invitation.getEmail().equalsIgnoreCase(email.trim())) {
            throw new ForbiddenException("This invitation was sent to a different email address");
        }
        return invitation;
    }

    /** Resolves the company an invitation belongs to. */
    @Transactional(readOnly = true)
    public Company requireInvitedCompany(ProjectInvitation invitation) {
        return companyRepository.findById(invitation.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", invitation.getCompanyId()));
    }

    /**
     * Attaches {@code user} to the invited project with the invited role and
     * consumes the invitation. The caller has already validated it.
     */
    @Transactional
    public ProjectMember complete(ProjectInvitation invitation, User user) {
        ProjectMember membership = projectMemberRepository
                .findByProjectIdAndUserId(invitation.getProjectId(), user.getUserId())
                .orElseGet(() -> new ProjectMember(
                        invitation.getProjectId(), user.getUserId(), invitation.getRole()));
        membership.setRole(invitation.getRole());
        ProjectMember saved = projectMemberRepository.save(membership);

        invitation.setUser(user);
        invitation.setStatus("accepted");
        invitationRepository.save(invitation);

        log.info("User {} accepted the invitation to project {} as {}",
                user.getEmail(), invitation.getProjectId(), invitation.getRole());
        return saved;
    }

    /** For a user who is already signed in and holds the invitation link. */
    @Transactional
    public ProjectMember accept(String token, User user) {
        ProjectInvitation invitation = requirePendingInvitation(token, user.getEmail());
        Company invitedCompany = requireInvitedCompany(invitation);

        if (user.getCompany() == null) {
            user.setCompany(invitedCompany);
            userRepository.save(user);
        } else if (!user.getCompany().getCompanyId().equals(invitedCompany.getCompanyId())) {
            throw new ConflictException(
                    "This invitation belongs to a different company workspace than your account");
        }
        return complete(invitation, user);
    }
}
