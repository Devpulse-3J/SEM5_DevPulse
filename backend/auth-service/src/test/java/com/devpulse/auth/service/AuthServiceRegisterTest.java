package com.devpulse.auth.service;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.dto.RegisterRequest;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.CompanyMember;
import com.devpulse.auth.entity.ProjectInvitation;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.DuplicateEmailException;
import com.devpulse.auth.exception.ForbiddenException;
import com.devpulse.auth.mapper.UserMapper;
import com.devpulse.auth.repository.CompanyMemberRepository;
import com.devpulse.auth.repository.CompanyRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.ProjectRepository;
import com.devpulse.auth.repository.UserRepository;
import com.devpulse.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import com.devpulse.auth.dto.UserProfileResponse;
import com.devpulse.auth.entity.Project;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link AuthServiceImpl}: registration (including against an email that
 * a project invite already pre-created a placeholder row for), and, in the nested
 * groups, switching the active company and building the {@code /auth/me} profile.
 */
public class AuthServiceRegisterTest {

    private UserRepository userRepository;
    private CompanyRepository companyRepository;
    private ProjectMemberRepository projectMemberRepository;
    private CompanyMemberRepository companyMemberRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private ProjectInvitationClaimService claimService;
    private AuthServiceImpl service;

    private Company invitingCompany;

    @BeforeEach
    public void setUp() {
        userRepository = mock(UserRepository.class);
        companyRepository = mock(CompanyRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        companyMemberRepository = mock(CompanyMemberRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        claimService = mock(ProjectInvitationClaimService.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        service = new AuthServiceImpl(userRepository, companyRepository, projectMemberRepository,
                companyMemberRepository, mock(ProjectRepository.class), passwordEncoder, jwtService,
                authenticationManager, new UserMapper(), claimService);

        invitingCompany = new Company();
        invitingCompany.setCompanyId(7);
        invitingCompany.setCompanyName("Inviting Org");

        when(passwordEncoder.encode(any())).thenReturn("$2a$10$encoded");
        when(jwtService.generateToken(any())).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(companyMemberRepository.findByUserIdAndCompanyId(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        when(companyMemberRepository.save(any(CompanyMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private User placeholderFromInvite() {
        User invited = new User();
        invited.setUserId(18);
        invited.setEmail("invitee@example.com");
        invited.setFullName("invitee");
        invited.setPasswordHash("$2a$10$randomUnknownHash");
        invited.setCompany(invitingCompany);
        invited.setSystemRole("member");
        invited.setMustResetPassword(true);
        return invited;
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("invitee@example.com");
        request.setPassword("chosen-password");
        request.setFullName("Invited Person");
        return request;
    }

    @Test
    public void registerClaimsInvitedPlaceholderInsteadOfRejectingIt() {
        User invited = placeholderFromInvite();
        when(userRepository.findByEmail("invitee@example.com")).thenReturn(Optional.of(invited));

        AuthResponse response = service.register(registerRequest());

        assertEquals("jwt-token", response.getAccessToken());
        // Same row: the project memberships attached at invite time survive.
        assertEquals(18, invited.getUserId());
        assertEquals("Invited Person", invited.getFullName());
        assertEquals("$2a$10$encoded", invited.getPasswordHash());
        assertFalse(invited.isMustResetPassword(), "claimed account is no longer a placeholder");
        verify(userRepository).save(invited);
        // No new company may be created for a claim.
        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    public void claimKeepsInvitingCompanyEvenWhenRequestAsksForANewOne() {
        User invited = placeholderFromInvite();
        when(userRepository.findByEmail("invitee@example.com")).thenReturn(Optional.of(invited));

        RegisterRequest request = registerRequest();
        request.setCompanyName("Breakaway Corp");
        request.setIsCompany(true);

        service.register(request);

        assertEquals(7, invited.getCompany().getCompanyId(),
                "claiming must not move the user out of the company that invited them");
        assertEquals("member", invited.getSystemRole(),
                "claiming must not escalate the role the inviter set");
        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    public void registerStillRejectsAFullyRegisteredEmail() {
        User existing = placeholderFromInvite();
        existing.setMustResetPassword(false);   // owner already chose a password
        when(userRepository.findByEmail("invitee@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("invitee@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> service.register(registerRequest()));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    public void claimedAccountCannotBeClaimedASecondTime() {
        User invited = placeholderFromInvite();
        when(userRepository.findByEmail("invitee@example.com")).thenReturn(Optional.of(invited));

        service.register(registerRequest());

        // Second attempt now sees a non-placeholder row.
        when(userRepository.existsByEmail("invitee@example.com")).thenReturn(true);
        assertThrows(DuplicateEmailException.class, () -> service.register(registerRequest()));
    }

    // -- registering with a project invitation token --------------------------

    private ProjectInvitation pendingInvitation() {
        ProjectInvitation invitation = new ProjectInvitation();
        invitation.setProjectId(3);
        invitation.setCompanyId(7);
        invitation.setEmail("invitee@example.com");
        invitation.setRole("manager");
        invitation.setToken("tok-123");
        return invitation;
    }

    @Test
    public void registeringWithAValidTokenJoinsTheInvitingCompanyAndProject() {
        ProjectInvitation invitation = pendingInvitation();
        when(claimService.requirePendingInvitation("tok-123", "invitee@example.com"))
                .thenReturn(invitation);
        when(claimService.requireInvitedCompany(invitation)).thenReturn(invitingCompany);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setUserId(55);
            return saved;
        });

        RegisterRequest request = registerRequest();
        request.setInviteToken("tok-123");
        AuthResponse response = service.register(request);

        assertEquals("jwt-token", response.getAccessToken());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(7, saved.getValue().getCompany().getCompanyId());
        assertEquals("member", saved.getValue().getSystemRole());
        assertEquals("$2a$10$encoded", saved.getValue().getPasswordHash());
        // The membership and the used-up invitation are handled for the saved user.
        verify(claimService).complete(invitation, saved.getValue());

        ArgumentCaptor<CompanyMember> membership = ArgumentCaptor.forClass(CompanyMember.class);
        verify(companyMemberRepository).save(membership.capture());
        assertEquals(55, membership.getValue().getUserId());
        assertEquals(7, membership.getValue().getCompanyId());
        assertEquals("member", membership.getValue().getRole());
    }

    @Test
    public void aTokenCannotBeUsedToOpenANewCompanyOrBecomeAdmin() {
        ProjectInvitation invitation = pendingInvitation();
        when(claimService.requirePendingInvitation("tok-123", "invitee@example.com"))
                .thenReturn(invitation);
        when(claimService.requireInvitedCompany(invitation)).thenReturn(invitingCompany);

        RegisterRequest request = registerRequest();
        request.setInviteToken("tok-123");
        request.setCompanyName("Breakaway Corp");
        request.setIsCompany(true);
        service.register(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(7, saved.getValue().getCompany().getCompanyId());
        assertEquals("member", saved.getValue().getSystemRole());
        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    public void aBadTokenFailsTheRegistrationInsteadOfCreatingAnOrphanAccount() {
        when(claimService.requirePendingInvitation("bad", "invitee@example.com"))
                .thenThrow(new IllegalArgumentException("This invitation has expired"));

        RegisterRequest request = registerRequest();
        request.setInviteToken("bad");

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verify(userRepository, never()).save(any(User.class));
        verify(claimService, never()).complete(any(), any());
    }

    @Test
    public void aTokenForADifferentAddressFailsTheRegistration() {
        when(claimService.requirePendingInvitation("tok-123", "invitee@example.com"))
                .thenThrow(new ForbiddenException("sent to a different email address"));

        RegisterRequest request = registerRequest();
        request.setInviteToken("tok-123");

        assertThrows(ForbiddenException.class, () -> service.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    public void aTokenDoesNotBypassTheDuplicateEmailCheck() {
        when(userRepository.existsByEmail("invitee@example.com")).thenReturn(true);

        RegisterRequest request = registerRequest();
        request.setInviteToken("tok-123");

        assertThrows(DuplicateEmailException.class, () -> service.register(request));
        verify(claimService, never()).complete(any(), any());
    }

    @Test
    public void registeringWithoutATokenStaysAnOrdinarySignup() {
        service.register(registerRequest());

        verify(claimService, never()).requirePendingInvitation(any(), any());
        verify(claimService, never()).complete(any(), any());
    }

    // -- company_members dual-write (additive, alongside users.company_id) ----

    @Test
    public void registeringAsACompanyCreatesItAndRecordsAnAdminCompanyMembership() {
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> {
            Company saved = inv.getArgument(0);
            saved.setCompanyId(9);
            return saved;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setUserId(41);
            return saved;
        });

        RegisterRequest request = registerRequest();
        request.setIsCompany(true);
        request.setCompanyName("New Co");
        service.register(request);

        ArgumentCaptor<CompanyMember> membership = ArgumentCaptor.forClass(CompanyMember.class);
        verify(companyMemberRepository).save(membership.capture());
        assertEquals(41, membership.getValue().getUserId());
        assertEquals(9, membership.getValue().getCompanyId());
        assertEquals("admin", membership.getValue().getRole());
    }

    @Test
    public void individualSignupWithoutACompanyRecordsNoCompanyMembership() {
        service.register(registerRequest());

        verify(companyMemberRepository, never()).save(any(CompanyMember.class));
    }

    /**
    * Covers {@code POST /auth/companies/{id}/switch}: issuing a token scoped to
    * a company other than the caller's home one.
    *
    * <p>Only a recorded {@code company_members} row makes this succeed - it is
    * the same table {@link ProjectMemberInviteTest} and
    * {@link AuthServiceRegisterTest} write to, so a cross-company invite (or
    * plain signup) is exactly what makes a later switch to that company work.
    */
    @Nested
    class SwitchCompany {
        private UserRepository userRepository;
        private CompanyMemberRepository companyMemberRepository;
        private CompanyRepository companyRepository;
        private ProjectRepository projectRepository;
        private JwtService jwtService;
        private AuthServiceImpl service;

        private User user;

        @BeforeEach
        public void setUp() {
            userRepository = mock(UserRepository.class);
            companyRepository = mock(CompanyRepository.class);
            projectRepository = mock(ProjectRepository.class);
            ProjectMemberRepository projectMemberRepository = mock(ProjectMemberRepository.class);
            companyMemberRepository = mock(CompanyMemberRepository.class);
            PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
            jwtService = mock(JwtService.class);
            AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
            ProjectInvitationClaimService claimService = mock(ProjectInvitationClaimService.class);

            service = new AuthServiceImpl(userRepository, companyRepository, projectMemberRepository,
                    companyMemberRepository, projectRepository, passwordEncoder, jwtService,
                    authenticationManager, new UserMapper(), claimService);

            user = new User();
            user.setUserId(20);
            user.setEmail("dev@example.com");
            user.setFullName("Dev Person");

            when(userRepository.findById(20)).thenReturn(Optional.of(user));
            when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        }

        @Test
        public void switchingToACompanyWithARecordedMembershipIssuesATokenScopedToIt() {
            when(companyMemberRepository.findByUserIdAndCompanyId(20, 9))
                    .thenReturn(Optional.of(new CompanyMember(20, 9, "member")));
            when(jwtService.generateToken(user, 9, "member")).thenReturn("switched-token");

            AuthResponse response = service.switchCompany(20, 9);

            assertEquals("switched-token", response.getAccessToken());
            assertEquals(9, response.getCompanyId());
            assertEquals("member", response.getSystemRole());
            assertEquals(20, response.getUserId());
        }

        @Test
        public void switchingToACompanyWithNoRecordedMembershipIsRejected() {
            when(companyMemberRepository.findByUserIdAndCompanyId(20, 9))
                    .thenReturn(Optional.empty());

            assertThrows(ForbiddenException.class, () -> service.switchCompany(20, 9));
            verify(jwtService, never()).generateToken(any(User.class), anyInt(), anyString());
        }

        @Test
        public void switchingAsAnUnknownUserFails() {
            when(userRepository.findById(99)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.switchCompany(99, 9));
        }

        @Test
        public void theIssuedTokenCarriesTheCompanyMembersRoleNotTheUsersHomeRole() {
            // The whole point of switching: an admin at home can only ever be
            // 'member' elsewhere (one-admin-per-user), and the token must reflect
            // that, not whatever users.system_role says.
            user.setSystemRole("admin");
            when(companyMemberRepository.findByUserIdAndCompanyId(20, 9))
                    .thenReturn(Optional.of(new CompanyMember(20, 9, "member")));
            when(jwtService.generateToken(user, 9, "member")).thenReturn("switched-token");

            AuthResponse response = service.switchCompany(20, 9);

            assertEquals("member", response.getSystemRole());
            ArgumentCaptor<String> role = ArgumentCaptor.forClass(String.class);
            verify(jwtService).generateToken(eq(user), eq(9), role.capture());
            assertEquals("member", role.getValue());
        }
    }

    /**
    * {@code GET /auth/me} for someone who belongs to more than one company.
    *
    * <p>The user here mirrors the real case that surfaced the gap: admin of their
    * home company (fcode) and a plain member of a second (Odin_eye) with a
    * developer role on a project there. The profile must describe the company the
    * token is scoped to, and tell the client which company each project is in -
    * otherwise it cannot know it must switch company before opening that project.
    */
    @Nested
    class Profile {
        private static final Integer HOME = 9;
        private static final Integer OTHER = 15;

        private UserRepository userRepository;
        private CompanyRepository companyRepository;
        private ProjectMemberRepository projectMemberRepository;
        private CompanyMemberRepository companyMemberRepository;
        private ProjectRepository projectRepository;
        private AuthServiceImpl service;

        private User user;
        private Company home;
        private Company other;

        @BeforeEach
        public void setUp() throws Exception {
            userRepository = mock(UserRepository.class);
            companyRepository = mock(CompanyRepository.class);
            projectMemberRepository = mock(ProjectMemberRepository.class);
            companyMemberRepository = mock(CompanyMemberRepository.class);
            projectRepository = mock(ProjectRepository.class);

            service = new AuthServiceImpl(userRepository, companyRepository, projectMemberRepository,
                    companyMemberRepository, projectRepository, mock(PasswordEncoder.class),
                    mock(JwtService.class), mock(AuthenticationManager.class), new UserMapper(),
                    mock(ProjectInvitationClaimService.class));

            home = company(HOME, "fcode");
            other = company(OTHER, "Odin_eye");

            user = new User();
            user.setUserId(22);
            user.setEmail("kalhara@example.com");
            user.setFullName("Kalhara");
            user.setCompany(home);
            user.setSystemRole("admin");

            when(userRepository.findById(22)).thenReturn(Optional.of(user));
            when(companyMemberRepository.findByUserId(22)).thenReturn(List.of(
                    new CompanyMember(22, HOME, "admin"),
                    new CompanyMember(22, OTHER, "member")));
            when(companyRepository.findAllById(any())).thenReturn(List.of(home, other));
            when(companyRepository.findById(OTHER)).thenReturn(Optional.of(other));
            when(companyRepository.findById(HOME)).thenReturn(Optional.of(home));
            when(projectMemberRepository.findByUserId(22)).thenReturn(List.of(
                    new ProjectMember(11, 22, "manager"),
                    new ProjectMember(8, 22, "developer")));
            when(projectRepository.findAllById(any())).thenReturn(List.of(
                    project(11, home, "Portfolio - testing"),
                    project(8, other, "Dev_pulse_Backend")));
        }

        private static Company company(Integer id, String name) throws Exception {
            Company c = new Company();
            c.setCompanyId(id);
            c.setCompanyName(name);
            return c;
        }

        private static Project project(Integer id, Company company, String name) throws Exception {
            Project p = new Project();
            set(p, "projectId", id);
            set(p, "company", company);
            set(p, "projectName", name);
            return p;
        }

        private static void set(Object target, String field, Object value) throws Exception {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        }

        @Test
        public void withoutAnActiveCompanyTheProfileIsTheHomeCompanyAndRole() {
            UserProfileResponse profile = service.getUserProfile(22);

            assertEquals(HOME, profile.getCompanyId());
            assertEquals("fcode", profile.getCompanyName());
            assertEquals("admin", profile.getSystemRole());
        }

        @Test
        public void eachProjectRoleSaysWhichCompanyTheProjectIsIn() {
            UserProfileResponse profile = service.getUserProfile(22);

            UserProfileResponse.ProjectRoleEntry odinProject = profile.getProjectRoles().stream()
                    .filter(p -> p.getProjectId().equals(8)).findFirst().orElseThrow();
            assertEquals(OTHER, odinProject.getCompanyId());
            assertEquals("Odin_eye", odinProject.getCompanyName());
            assertEquals("Dev_pulse_Backend", odinProject.getProjectName());
            assertEquals("developer", odinProject.getRole());

            UserProfileResponse.ProjectRoleEntry homeProject = profile.getProjectRoles().stream()
                    .filter(p -> p.getProjectId().equals(11)).findFirst().orElseThrow();
            assertEquals(HOME, homeProject.getCompanyId());
        }

        @Test
        public void everyCompanyTheUserBelongsToIsListedWithTheirRoleThere() {
            UserProfileResponse profile = service.getUserProfile(22);

            assertEquals(2, profile.getCompanies().size());
            UserProfileResponse.CompanyEntry odin = profile.getCompanies().stream()
                    .filter(c -> c.getCompanyId().equals(OTHER)).findFirst().orElseThrow();
            assertEquals("Odin_eye", odin.getCompanyName());
            assertEquals("member", odin.getRole());
        }

        @Test
        public void aTokenScopedToASecondCompanyMakesTheProfileDescribeThatCompany() {
            UserProfileResponse profile = service.getUserProfile(22, OTHER);

            assertEquals(OTHER, profile.getCompanyId());
            assertEquals("Odin_eye", profile.getCompanyName());
            assertEquals("member", profile.getSystemRole(),
                    "an admin at home is only a member of a second company - the role must not follow them");
        }

        @Test
        public void aCompanyTheUserDoesNotBelongToFallsBackToTheHomeCompany() {
            UserProfileResponse profile = service.getUserProfile(22, 999);

            assertEquals(HOME, profile.getCompanyId());
            assertEquals("admin", profile.getSystemRole());
        }

        @Test
        public void aUserWithNoCompanyStillGetsAProfile() {
            User loner = new User();
            loner.setUserId(30);
            loner.setEmail("loner@example.com");
            loner.setSystemRole("member");
            when(userRepository.findById(30)).thenReturn(Optional.of(loner));
            when(companyMemberRepository.findByUserId(30)).thenReturn(List.of());
            when(projectMemberRepository.findByUserId(30)).thenReturn(List.of());
            when(projectRepository.findAllById(any())).thenReturn(List.of());
            when(companyRepository.findAllById(any())).thenReturn(List.of());

            UserProfileResponse profile = service.getUserProfile(30);

            assertNull(profile.getCompanyId());
            assertNull(profile.getCompanyName());
            assertTrue(profile.getProjectRoles().isEmpty());
            assertTrue(profile.getCompanies().isEmpty());
        }
    }
}
