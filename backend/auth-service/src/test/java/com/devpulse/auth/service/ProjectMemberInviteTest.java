package com.devpulse.auth.service;

import com.devpulse.auth.dto.InviteByEmailRequest;
import com.devpulse.auth.dto.InviteResultResponse;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.ProjectInvitation;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ConflictException;
import com.devpulse.auth.repository.ProjectInvitationRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.UserRepository;
import com.devpulse.auth.security.ProjectAccessService;
import com.devpulse.auth.security.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@code POST /projects/{id}/invite}.
 *
 * <p>An address that already has an account is added straight to the project. An
 * address with none gets a pending, expiring {@code project_invitations} row keyed
 * by email, and inviting must never write to the users table (pre-creating a
 * placeholder account there collided with the UNIQUE constraint on
 * {@code users.email}).
 *
 * <p>The role written to that row must satisfy the table's CHECK constraint
 * ({@code 'manager'} or {@code 'developer'}, lowercase). Writing the enum name
 * ({@code DEVELOPER}) broke every invite to an unregistered address with a 500.
 */
public class ProjectMemberInviteTest {

    private static final Integer COMPANY_ID = 6;
    private static final Integer PROJECT_ID = 3;
    private static final String FRONTEND = "https://app.devpulse.test";

    private ProjectMemberRepository projectMemberRepository;
    private UserRepository userRepository;
    private ProjectInvitationRepository projectInvitationRepository;
    private JavaMailSender mailSender;
    private ProjectMemberServiceImpl service;

    private RequestContext context;
    private Company company;
    private User admin;

    @BeforeEach
    public void setUp() {
        projectMemberRepository = mock(ProjectMemberRepository.class);
        userRepository = mock(UserRepository.class);
        projectInvitationRepository = mock(ProjectInvitationRepository.class);
        mailSender = mock(JavaMailSender.class);
        ProjectAccessService projectAccessService = mock(ProjectAccessService.class);

        // Trailing slash on purpose: links must not contain a double slash.
        service = new ProjectMemberServiceImpl(projectMemberRepository, userRepository,
                projectAccessService, projectInvitationRepository, FRONTEND + "/", mailSender);

        context = new RequestContext(1, COMPANY_ID);

        company = new Company();
        company.setCompanyId(COMPANY_ID);
        company.setCompanyName("Acme");

        admin = new User();
        admin.setUserId(1);
        admin.setEmail("admin@acme.test");
        admin.setCompany(company);

        when(projectAccessService.requireAdmin(any())).thenReturn(admin);
        when(projectMemberRepository.save(any(ProjectMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(projectInvitationRepository.save(any(ProjectInvitation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(projectInvitationRepository
                .findByProjectIdAndEmailIgnoreCaseAndStatus(anyInt(), anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    private InviteByEmailRequest request(String email) {
        return new InviteByEmailRequest(email, "DEVELOPER");
    }

    private ProjectInvitation savedInvitation() {
        ArgumentCaptor<ProjectInvitation> captor = ArgumentCaptor.forClass(ProjectInvitation.class);
        verify(projectInvitationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    public void invitingAnUnregisteredEmailRecordsAPendingProjectInvitation() {
        when(userRepository.findByEmailIgnoreCase("newcomer@example.com"))
                .thenReturn(Optional.empty());
        OffsetDateTime before = OffsetDateTime.now();

        InviteResultResponse response =
                service.inviteByEmail(context, PROJECT_ID, request("  NewComer@Example.com "));

        assertEquals(InviteResultResponse.INVITED_NEW_USER, response.getStatus());
        ProjectInvitation invitation = savedInvitation();
        assertEquals(PROJECT_ID, invitation.getProjectId());
        assertEquals(COMPANY_ID, invitation.getCompanyId());
        assertEquals("newcomer@example.com", invitation.getEmail(),
                "stored lowercase, because the unique index is on lower(email)");
        assertEquals("pending", invitation.getStatus());
        assertSame(admin, invitation.getInvitedBy());
        assertNull(invitation.getUser(), "no account exists yet");
        assertNotNull(invitation.getToken());
        assertFalse(invitation.getToken().isBlank());
        assertTrue(invitation.getExpiresAt().isAfter(before.plusDays(6)));
        assertTrue(invitation.getExpiresAt().isBefore(before.plusDays(8)));

        verify(userRepository, never()).save(any(User.class));
        verify(projectMemberRepository, never()).save(any(ProjectMember.class));
    }

    @Test
    public void theRoleStoredSatisfiesTheTablesCheckConstraint() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        service.inviteByEmail(context, PROJECT_ID, request("dev@example.com"));

        // project_invitations_role_check allows only these two values, lowercase.
        assertTrue(Set.of("manager", "developer").contains(savedInvitation().getRole()),
                "stored role was: " + savedInvitation().getRole());
    }

    @Test
    public void aManagerInviteIsStoredAsManager() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        service.inviteByEmail(context, PROJECT_ID, new InviteByEmailRequest("m@example.com", "MANAGER"));

        assertEquals("manager", savedInvitation().getRole());
    }

    @Test
    public void reInvitingAPendingAddressRefreshesTheSameRowInsteadOfAddingAnother() {
        when(userRepository.findByEmailIgnoreCase("newcomer@example.com"))
                .thenReturn(Optional.empty());
        ProjectInvitation pending = new ProjectInvitation();
        pending.setToken("old-token");
        pending.setRole("developer");
        pending.setStatus("pending");
        when(projectInvitationRepository.findByProjectIdAndEmailIgnoreCaseAndStatus(
                PROJECT_ID, "newcomer@example.com", "pending")).thenReturn(Optional.of(pending));

        service.inviteByEmail(context, PROJECT_ID,
                new InviteByEmailRequest("newcomer@example.com", "MANAGER"));

        assertSame(pending, savedInvitation(), "the existing pending row is updated in place");
        assertNotEquals("old-token", pending.getToken(), "the old link stops working");
        assertEquals("manager", pending.getRole());
        assertEquals("pending", pending.getStatus());
    }

    @Test
    public void theEmailLinksToTheConfiguredFrontendAndCarriesTheToken() {
        when(userRepository.findByEmailIgnoreCase("newcomer@example.com"))
                .thenReturn(Optional.empty());

        service.inviteByEmail(context, PROJECT_ID, request("newcomer@example.com"));

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        String body = mail.getValue().getText();
        String token = savedInvitation().getToken();

        assertEquals("newcomer@example.com", mail.getValue().getTo()[0]);
        assertTrue(body.contains(FRONTEND + "/register?invite=" + token + "&email=newcomer%40example.com"),
                "body was: " + body);
        assertFalse(body.contains("localhost"), "links must not be hardcoded to localhost");
        assertFalse(body.contains("//register"), "a trailing slash on the base URL must be trimmed");
    }

    @Test
    public void aFailedEmailSendDoesNotUndoTheRecordedInvitation() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        InviteResultResponse response = service.inviteByEmail(context, PROJECT_ID, request("a@example.com"));

        assertEquals(InviteResultResponse.INVITED_NEW_USER, response.getStatus());
        verify(projectInvitationRepository).save(any(ProjectInvitation.class));
    }

    @Test
    public void invitingAnExistingMemberOfThisCompanyAddsThemDirectly() {
        User existing = new User();
        existing.setUserId(20);
        existing.setEmail("member@example.com");
        existing.setCompany(company);
        when(userRepository.findByEmailIgnoreCase("member@example.com"))
                .thenReturn(Optional.of(existing));
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, 20))
                .thenReturn(Optional.empty());

        InviteResultResponse response =
                service.inviteByEmail(context, PROJECT_ID, request("member@example.com"));

        assertEquals(InviteResultResponse.ADDED_EXISTING_USER, response.getStatus());
        assertEquals(20, response.getUserId());
        assertEquals("DEVELOPER", response.getRole());
        verify(projectMemberRepository).save(any(ProjectMember.class));
        verify(userRepository, never()).save(any(User.class));
        verify(projectInvitationRepository, never()).save(any(ProjectInvitation.class));
    }

    @Test
    public void theExistingMemberEmailLinksToTheConfiguredFrontendToo() {
        User existing = new User();
        existing.setUserId(20);
        existing.setEmail("member@example.com");
        existing.setFullName("Member");
        existing.setCompany(company);
        when(userRepository.findByEmailIgnoreCase("member@example.com"))
                .thenReturn(Optional.of(existing));
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, 20))
                .thenReturn(Optional.empty());

        service.inviteByEmail(context, PROJECT_ID, request("member@example.com"));

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertTrue(mail.getValue().getText().contains(FRONTEND + "/login"));
        assertFalse(mail.getValue().getText().contains("localhost"));
    }

    @Test
    public void anAccountStoredInADifferentCaseIsRecognisedAsAlreadyRegistered() {
        User existing = new User();
        existing.setUserId(21);
        existing.setEmail("Mixed.Case@Example.com");
        existing.setCompany(company);
        // The service lowercases before looking up; the account is only found
        // because the lookup ignores case. Otherwise a registered person would
        // read as unregistered and be sent a registration invitation instead.
        when(userRepository.findByEmailIgnoreCase("mixed.case@example.com"))
                .thenReturn(Optional.of(existing));
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, 21))
                .thenReturn(Optional.empty());

        InviteResultResponse response =
                service.inviteByEmail(context, PROJECT_ID, request("Mixed.Case@Example.com"));

        assertEquals(InviteResultResponse.ADDED_EXISTING_USER, response.getStatus());
    }

    @Test
    public void invitingAnAddressOwnedByAnotherCompanyIsRejected() {
        Company otherCompany = new Company();
        otherCompany.setCompanyId(1);

        User elsewhere = new User();
        elsewhere.setUserId(20);
        elsewhere.setEmail("taken@example.com");
        elsewhere.setCompany(otherCompany);
        when(userRepository.findByEmailIgnoreCase("taken@example.com"))
                .thenReturn(Optional.of(elsewhere));

        // users.email is globally UNIQUE, so one address cannot exist in two
        // companies. This is the case that stranded a self-registered developer
        // in the default company where no admin could reach them.
        assertThrows(ConflictException.class,
                () -> service.inviteByEmail(context, PROJECT_ID, request("taken@example.com")));
        verify(userRepository, never()).save(any(User.class));
        verify(projectMemberRepository, never()).save(any(ProjectMember.class));
        verify(projectInvitationRepository, never()).save(any(ProjectInvitation.class));
    }

    @Test
    public void reInvitingSomeoneAlreadyOnTheProjectUpdatesTheirRole() {
        User existing = new User();
        existing.setUserId(20);
        existing.setEmail("member@example.com");
        existing.setCompany(company);
        ProjectMember membership = new ProjectMember(PROJECT_ID, 20, "developer");

        when(userRepository.findByEmailIgnoreCase("member@example.com"))
                .thenReturn(Optional.of(existing));
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, 20))
                .thenReturn(Optional.of(membership));

        service.inviteByEmail(context, PROJECT_ID,
                new InviteByEmailRequest("member@example.com", "MANAGER"));

        assertEquals("manager", membership.getRole(),
                "re-inviting updates the role rather than failing on the unique constraint");
        verify(projectMemberRepository).save(membership);
    }
}
