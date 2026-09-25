package com.devpulse.auth.service;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.entity.CompanyMember;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ForbiddenException;
import com.devpulse.auth.exception.ResourceNotFoundException;
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
 * Covers {@code POST /auth/companies/{id}/switch}: issuing a token scoped to
 * a company other than the caller's home one.
 *
 * <p>Only a recorded {@code company_members} row makes this succeed - it is
 * the same table {@link ProjectMemberInviteTest} and
 * {@link AuthServiceRegisterTest} write to, so a cross-company invite (or
 * plain signup) is exactly what makes a later switch to that company work.
 */
public class AuthServiceSwitchCompanyTest {

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
