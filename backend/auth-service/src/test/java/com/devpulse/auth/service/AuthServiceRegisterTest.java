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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers registration against an email that a project invite already
 * pre-created a placeholder row for.
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
}
