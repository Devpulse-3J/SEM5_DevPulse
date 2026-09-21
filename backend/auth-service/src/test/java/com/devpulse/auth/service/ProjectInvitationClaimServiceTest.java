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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A claim needs BOTH the emailed token and a matching address. The address alone
 * is not enough: accounts are not email-verified, so anyone could register an
 * invited address first and take the invitee's project role.
 */
public class ProjectInvitationClaimServiceTest {

    private static final Integer COMPANY_ID = 6;
    private static final Integer PROJECT_ID = 3;
    private static final String TOKEN = "tok-123";
    private static final String EMAIL = "invitee@example.com";

    private ProjectInvitationRepository invitationRepository;
    private ProjectMemberRepository projectMemberRepository;
    private CompanyRepository companyRepository;
    private UserRepository userRepository;
    private ProjectInvitationClaimService service;

    private Company company;
    private ProjectInvitation invitation;

    @BeforeEach
    public void setUp() {
        invitationRepository = mock(ProjectInvitationRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        companyRepository = mock(CompanyRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ProjectInvitationClaimService(
                invitationRepository, projectMemberRepository, companyRepository, userRepository);

        company = new Company();
        company.setCompanyId(COMPANY_ID);
        company.setCompanyName("Acme");

        invitation = new ProjectInvitation();
        invitation.setProjectId(PROJECT_ID);
        invitation.setCompanyId(COMPANY_ID);
        invitation.setEmail(EMAIL);
        invitation.setRole("manager");
        invitation.setToken(TOKEN);
        invitation.setStatus("pending");
        invitation.setExpiresAt(OffsetDateTime.now().plusDays(3));

        when(invitationRepository.findByToken(TOKEN)).thenReturn(Optional.of(invitation));
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(projectMemberRepository.save(any(ProjectMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(invitationRepository.save(any(ProjectInvitation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User user(String email, Company userCompany) {
        User user = new User();
        user.setUserId(42);
        user.setEmail(email);
        user.setCompany(userCompany);
        return user;
    }

    // -- requirePendingInvitation --------------------------------------------

    @Test
    public void aValidTokenForTheRightAddressIsAccepted() {
        assertSame(invitation, service.requirePendingInvitation(TOKEN, EMAIL));
    }

    @Test
    public void theAddressComparisonIgnoresCaseAndWhitespace() {
        assertSame(invitation, service.requirePendingInvitation(" " + TOKEN + " ", "  Invitee@Example.COM "));
    }

    @Test
    public void anUnknownTokenIsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.requirePendingInvitation("nope", EMAIL));
    }

    @Test
    public void aBlankTokenIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.requirePendingInvitation("  ", EMAIL));
        assertThrows(IllegalArgumentException.class,
                () -> service.requirePendingInvitation(null, EMAIL));
    }

    @Test
    public void anExpiredInvitationIsRejected() {
        invitation.setExpiresAt(OffsetDateTime.now().minusMinutes(1));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.requirePendingInvitation(TOKEN, EMAIL));
        assertTrue(error.getMessage().contains("expired"));
    }

    @Test
    public void anInvitationThatWasAlreadyUsedIsRejected() {
        invitation.setStatus("accepted");

        assertThrows(IllegalArgumentException.class,
                () -> service.requirePendingInvitation(TOKEN, EMAIL));
    }

    @Test
    public void theTokenAloneIsNotEnoughForADifferentAddress() {
        assertThrows(ForbiddenException.class,
                () -> service.requirePendingInvitation(TOKEN, "someone.else@example.com"));
    }

    // -- complete -------------------------------------------------------------

    @Test
    public void completingAnInvitationCreatesTheMembershipWithTheInvitedRole() {
        User user = user(EMAIL, company);
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, 42))
                .thenReturn(Optional.empty());

        ProjectMember membership = service.complete(invitation, user);

        assertEquals(PROJECT_ID, membership.getProjectId());
        assertEquals(42, membership.getUserId());
        assertEquals("manager", membership.getRole());
        assertEquals("accepted", invitation.getStatus());
        assertSame(user, invitation.getUser());
        verify(invitationRepository).save(invitation);
    }

    @Test
    public void completingUpdatesTheRoleIfTheUserIsAlreadyOnTheProject() {
        ProjectMember existing = new ProjectMember(PROJECT_ID, 42, "developer");
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, 42))
                .thenReturn(Optional.of(existing));

        service.complete(invitation, user(EMAIL, company));

        assertEquals("manager", existing.getRole());
        verify(projectMemberRepository).save(existing);
    }

    // -- accept (already signed in) ------------------------------------------

    @Test
    public void aSignedInUserWithNoCompanyJoinsTheInvitingCompany() {
        User user = user(EMAIL, null);
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, 42))
                .thenReturn(Optional.empty());

        ProjectMember membership = service.accept(TOKEN, user);

        assertSame(company, user.getCompany());
        verify(userRepository).save(user);
        assertEquals("manager", membership.getRole());
        assertEquals("accepted", invitation.getStatus());
    }

    @Test
    public void aSignedInUserOfTheSameCompanyJustGetsTheMembership() {
        User user = user(EMAIL, company);
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, 42))
                .thenReturn(Optional.empty());

        service.accept(TOKEN, user);

        verify(userRepository, never()).save(any(User.class));
        ArgumentCaptor<ProjectMember> saved = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(saved.capture());
        assertEquals(PROJECT_ID, saved.getValue().getProjectId());
    }

    @Test
    public void aUserOfAnotherCompanyCannotAcceptAndNothingIsWritten() {
        Company other = new Company();
        other.setCompanyId(99);

        assertThrows(ConflictException.class, () -> service.accept(TOKEN, user(EMAIL, other)));

        verify(projectMemberRepository, never()).save(any(ProjectMember.class));
        assertEquals("pending", invitation.getStatus());
    }

    @Test
    public void aSignedInUserWithADifferentEmailCannotAcceptSomeoneElsesInvitation() {
        assertThrows(ForbiddenException.class,
                () -> service.accept(TOKEN, user("intruder@example.com", company)));

        verify(projectMemberRepository, never()).save(any(ProjectMember.class));
    }
}
