package com.devpulse.auth.service;

import com.devpulse.auth.dto.*;
import com.devpulse.auth.entity.*;
import com.devpulse.auth.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class WorkspaceInviteServiceTest {

    private CompanyRepository companyRepository;
    private UserRepository userRepository;
    private ProjectMemberRepository projectMemberRepository;
    private OrganizationInvitationRepository orgInviteRepository;
    private WorkspaceJoinRequestRepository joinRequestRepository;
    private ProjectInvitationRepository projectInviteRepository;
    private WorkspaceInviteServiceImpl service;

    private Company company;
    private User adminUser;
    private User regularUser;

    @BeforeEach
    public void setUp() {
        companyRepository = mock(CompanyRepository.class);
        userRepository = mock(UserRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        orgInviteRepository = mock(OrganizationInvitationRepository.class);
        joinRequestRepository = mock(WorkspaceJoinRequestRepository.class);
        projectInviteRepository = mock(ProjectInvitationRepository.class);

        service = new WorkspaceInviteServiceImpl(
                companyRepository, userRepository, projectMemberRepository,
                orgInviteRepository, joinRequestRepository, projectInviteRepository
        );

        company = new Company();
        company.setCompanyId(1);
        company.setCompanyName("DevPulse Org");

        adminUser = new User();
        adminUser.setUserId(10);
        adminUser.setEmail("admin@devpulse.com");
        adminUser.setCompany(company);
        adminUser.setSystemRole("admin");

        regularUser = new User();
        regularUser.setUserId(20);
        regularUser.setEmail("user@devpulse.com");
        regularUser.setCompany(null);
        regularUser.setSystemRole("member");
    }

    @Test
    public void testInviteToOrganizationSuccessfulByAdmin() {
        OrganizationInviteRequest request = new OrganizationInviteRequest(1, "newuser@devpulse.com", "member");
        when(companyRepository.findById(1)).thenReturn(Optional.of(company));
        when(orgInviteRepository.save(any(OrganizationInvitation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrganizationInvitation result = service.inviteToOrganization(adminUser, request);

        assertNotNull(result);
        assertEquals("newuser@devpulse.com", result.getEmail());
        assertEquals("pending", result.getStatus());
        verify(orgInviteRepository).save(any(OrganizationInvitation.class));
    }

    @Test
    public void testInviteToOrganizationAccessDeniedForNonAdmin() {
        OrganizationInviteRequest request = new OrganizationInviteRequest(1, "newuser@devpulse.com", "member");
        regularUser.setCompany(company);

        assertThrows(AccessDeniedException.class, () -> service.inviteToOrganization(regularUser, request));
    }

    @Test
    public void testAcceptOrganizationInviteSuccessful() {
        OrganizationInvitation invitation = new OrganizationInvitation(
                company, "user@devpulse.com", "member", "token-123", adminUser, OffsetDateTime.now().plusDays(1)
        );

        when(orgInviteRepository.findByToken("token-123")).thenReturn(Optional.of(invitation));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.acceptOrganizationInvite("token-123", regularUser);

        assertNotNull(result);
        assertEquals(company, result.getCompany());
        assertEquals("accepted", invitation.getStatus());
    }

    @Test
    public void testRequestToJoinWorkspaceSuccessful() {
        JoinWorkspaceRequestDto requestDto = new JoinWorkspaceRequestDto("octocat", "Please let me join");
        when(companyRepository.findById(1)).thenReturn(Optional.of(company));
        when(joinRequestRepository.findByCompanyCompanyIdAndUserUserId(1, 20)).thenReturn(Optional.empty());
        when(joinRequestRepository.save(any(WorkspaceJoinRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceJoinRequest result = service.requestToJoinWorkspace(regularUser, 1, requestDto);

        assertNotNull(result);
        assertEquals("pending", result.getStatus());
        assertEquals("octocat", result.getGithubUsername());
    }

    @Test
    public void testApproveJoinRequestSuccessful() {
        WorkspaceJoinRequest joinRequest = new WorkspaceJoinRequest(company, regularUser, "octocat", "Let me in");
        joinRequest.setRequestId(100);

        when(joinRequestRepository.findById(100)).thenReturn(Optional.of(joinRequest));
        when(joinRequestRepository.save(any(WorkspaceJoinRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceJoinRequest result = service.approveJoinRequest(adminUser, 1, 100);

        assertNotNull(result);
        assertEquals("approved", result.getStatus());
        assertEquals(adminUser, result.getReviewedBy());
        assertEquals(company, regularUser.getCompany());
    }
}
