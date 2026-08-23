package com.devpulse.auth.service;

import com.devpulse.auth.dto.InviteByEmailRequest;
import com.devpulse.auth.dto.InviteResultResponse;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ConflictException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.repository.OrganizationInvitationRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.UserRepository;
import com.devpulse.auth.security.ProjectAccessService;
import com.devpulse.auth.security.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the rule this endpoint exists to obey: an admin may only invite someone
 * who already has an account, and inviting must never write to the users table.
 * Pre-creating a placeholder account there is what used to collide with the
 * UNIQUE constraint on {@code users.email} and fail the whole request.
 */
public class ProjectMemberInviteTest {

    private static final Integer COMPANY_ID = 6;
    private static final Integer PROJECT_ID = 3;

    private ProjectMemberRepository projectMemberRepository;
    private UserRepository userRepository;
    private OrganizationInvitationRepository orgInviteRepository;
    private ProjectMemberServiceImpl service;

    private RequestContext context;
    private Company company;

    @BeforeEach
    public void setUp() {
        projectMemberRepository = mock(ProjectMemberRepository.class);
        userRepository = mock(UserRepository.class);
        orgInviteRepository = mock(OrganizationInvitationRepository.class);
        ProjectAccessService projectAccessService = mock(ProjectAccessService.class);

        service = new ProjectMemberServiceImpl(
                projectMemberRepository, userRepository, projectAccessService, orgInviteRepository, null);

        context = new RequestContext(1, COMPANY_ID);

        company = new Company();
        company.setCompanyId(COMPANY_ID);
        company.setCompanyName("Acme");

        User admin = new User();
        admin.setUserId(1);
        admin.setEmail("admin@acme.test");
        admin.setCompany(company);

        when(projectAccessService.requireAdmin(any())).thenReturn(admin);
        when(projectMemberRepository.save(any(ProjectMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private InviteByEmailRequest request(String email) {
        return new InviteByEmailRequest(email, "DEVELOPER");
    }

    @Test
    public void invitingAnUnregisteredEmailCreatesOrganizationInviteRecord() {
        when(userRepository.findByEmailIgnoreCase("newcomer@example.com"))
                .thenReturn(Optional.empty());

        InviteResultResponse response = service.inviteByEmail(context, PROJECT_ID, request("newcomer@example.com"));

        assertEquals(InviteResultResponse.INVITED_NEW_USER, response.getStatus());
        verify(orgInviteRepository).save(any());
        verify(userRepository, never()).save(any(User.class));
        verify(projectMemberRepository, never()).save(any(ProjectMember.class));
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
    }

    @Test
    public void anAccountStoredInADifferentCaseIsRecognisedAsAlreadyRegistered() {
        User existing = new User();
        existing.setUserId(21);
        existing.setEmail("Mixed.Case@Example.com");
        existing.setCompany(company);
        // The service lowercases before looking up; the account is only found
        // because the lookup ignores case. Otherwise a registered person would
        // read as unregistered and the invite would 404 on them.
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
