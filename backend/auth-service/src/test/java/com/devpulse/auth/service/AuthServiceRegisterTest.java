package com.devpulse.auth.service;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.dto.RegisterRequest;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.DuplicateEmailException;
import com.devpulse.auth.mapper.UserMapper;
import com.devpulse.auth.repository.CompanyRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.UserRepository;
import com.devpulse.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthServiceImpl service;

    private Company invitingCompany;

    @BeforeEach
    public void setUp() {
        userRepository = mock(UserRepository.class);
        companyRepository = mock(CompanyRepository.class);
        ProjectMemberRepository projectMemberRepository = mock(ProjectMemberRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        service = new AuthServiceImpl(userRepository, companyRepository, projectMemberRepository,
                passwordEncoder, jwtService, authenticationManager, new UserMapper());

        invitingCompany = new Company();
        invitingCompany.setCompanyId(7);
        invitingCompany.setCompanyName("Inviting Org");

        when(passwordEncoder.encode(any())).thenReturn("$2a$10$encoded");
        when(jwtService.generateToken(any())).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
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
}
