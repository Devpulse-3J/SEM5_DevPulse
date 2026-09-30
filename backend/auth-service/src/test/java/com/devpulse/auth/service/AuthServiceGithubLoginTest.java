package com.devpulse.auth.service;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.dto.GithubLoginRequest;
import com.devpulse.auth.dto.GithubUserInfo;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.CompanyMember;
import com.devpulse.auth.entity.ProjectInvitation;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.SystemRole;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ConflictException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceGithubLoginTest {

    private UserRepository userRepository;
    private CompanyRepository companyRepository;
    private ProjectMemberRepository projectMemberRepository;
    private CompanyMemberRepository companyMemberRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private ProjectInvitationClaimService claimService;
    private GithubIdentityService githubIdentityService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        companyRepository = mock(CompanyRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        companyMemberRepository = mock(CompanyMemberRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        claimService = mock(ProjectInvitationClaimService.class);
        githubIdentityService = mock(GithubIdentityService.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        authService = new AuthServiceImpl(
                userRepository,
                companyRepository,
                projectMemberRepository,
                companyMemberRepository,
                mock(ProjectRepository.class),
                passwordEncoder,
                jwtService,
                authenticationManager,
                new UserMapper(),
                claimService,
                githubIdentityService
        );

        when(jwtService.generateToken(any(User.class))).thenReturn("mock-jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getUserId() == null) {
                u.setUserId(99);
            }
            return u;
        });
        when(companyMemberRepository.findByUserIdAndCompanyId(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        when(companyMemberRepository.save(any(CompanyMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void newGitHubUserSignsUpSuccessfully() {
        when(githubIdentityService.exchangeCodeAndFetchUserInfo("valid-code"))
                .thenReturn(new GithubUserInfo(12345L, "octocat", "The Octocat", "octocat@github.com", "https://avatar.url"));
        when(userRepository.findFirstByGithubId(12345L)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("octocat@github.com")).thenReturn(Optional.empty());

        AuthResponse response = authService.loginWithGithub(new GithubLoginRequest("valid-code"));

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.getEmail()).isEqualTo("octocat@github.com");
        assertThat(response.getFullName()).isEqualTo("The Octocat");
        assertThat(response.getGithubId()).isEqualTo(12345L);
        assertThat(response.getGithubUsername()).isEqualTo("octocat");
        assertThat(response.getAuthProvider()).isEqualTo("GITHUB");
        assertThat(response.getAvatarUrl()).isEqualTo("https://avatar.url");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getPasswordHash()).isNull();
        assertThat(savedUser.getGithubId()).isEqualTo(12345L);
        assertThat(savedUser.getGithubUsername()).isEqualTo("octocat");
        assertThat(savedUser.getAvatarUrl()).isEqualTo("https://avatar.url");
        assertThat(savedUser.getAuthProvider()).isEqualTo("GITHUB");
        assertThat(savedUser.getSystemRoleEnum()).isEqualTo(SystemRole.MEMBER);
    }

    @Test
    void existingUserByGithubIdLogsInDirectly() {
        User existing = new User();
        existing.setUserId(10);
        existing.setEmail("existing@devpulse.io");
        existing.setFullName("Existing User");
        existing.setGithubId(12345L);
        existing.setGithubUsername("oldlogin");
        existing.setSystemRoleEnum(SystemRole.MEMBER);

        when(githubIdentityService.exchangeCodeAndFetchUserInfo("valid-code"))
                .thenReturn(new GithubUserInfo(12345L, "newlogin", "Existing User", "existing@devpulse.io", "https://new-avatar.png"));
        when(userRepository.findFirstByGithubId(12345L)).thenReturn(Optional.of(existing));

        AuthResponse response = authService.loginWithGithub(new GithubLoginRequest("valid-code"));

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.getEmail()).isEqualTo("existing@devpulse.io");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getGithubUsername()).isEqualTo("newlogin");
        assertThat(saved.getAvatarUrl()).isEqualTo("https://new-avatar.png");
    }

    @Test
    void existingUserByEmailLinksGitHubAccountOnFirstOAuthLogin() {
        User existing = new User();
        existing.setUserId(15);
        existing.setEmail("localuser@devpulse.io");
        existing.setFullName("Local User");
        existing.setPasswordHash("$2a$10$hashedPassword");
        existing.setSystemRoleEnum(SystemRole.MEMBER);

        when(githubIdentityService.exchangeCodeAndFetchUserInfo("valid-code"))
                .thenReturn(new GithubUserInfo(55555L, "localgh", "Local User", "localuser@devpulse.io", "https://avatar.png"));
        when(userRepository.findFirstByGithubId(55555L)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("localuser@devpulse.io")).thenReturn(Optional.of(existing));

        AuthResponse response = authService.loginWithGithub(new GithubLoginRequest("valid-code"));

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-jwt-token");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getGithubId()).isEqualTo(55555L);
        assertThat(saved.getGithubUsername()).isEqualTo("localgh");
        // Original local password hash remains intact
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$hashedPassword");
    }

    @Test
    void conflictingUserWithDifferentGithubIdThrowsConflictException() {
        User existing = new User();
        existing.setUserId(20);
        existing.setEmail("someone@devpulse.io");
        existing.setGithubId(99999L); // already linked to a different github account

        when(githubIdentityService.exchangeCodeAndFetchUserInfo("valid-code"))
                .thenReturn(new GithubUserInfo(11111L, "newgh", "Someone", "someone@devpulse.io", null));
        when(userRepository.findFirstByGithubId(11111L)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("someone@devpulse.io")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.loginWithGithub(new GithubLoginRequest("valid-code")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists and is linked to another GitHub account");
    }

    @Test
    void newGitHubUserWithInviteTokenClaimsProjectAndWorkspace() {
        Company invitedCompany = new Company();
        invitedCompany.setCompanyId(3);
        invitedCompany.setCompanyName("Inviting Team");

        ProjectInvitation invitation = new ProjectInvitation();
        invitation.setInvitationId(1);
        invitation.setCompanyId(3);
        invitation.setProjectId(8);
        invitation.setEmail("invited@github.com");
        invitation.setRole("developer");
        invitation.setStatus("pending");

        when(githubIdentityService.exchangeCodeAndFetchUserInfo("valid-code"))
                .thenReturn(new GithubUserInfo(77777L, "invitee", "Invited User", "invited@github.com", null));
        when(userRepository.findFirstByGithubId(77777L)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("invited@github.com")).thenReturn(Optional.empty());

        when(claimService.requirePendingInvitation("token-xyz", "invited@github.com")).thenReturn(invitation);
        when(claimService.requireInvitedCompany(invitation)).thenReturn(invitedCompany);
        when(claimService.complete(any(), any())).thenReturn(new ProjectMember(8, 99, "developer"));

        AuthResponse response = authService.loginWithGithub(new GithubLoginRequest("valid-code", "token-xyz"));

        assertThat(response).isNotNull();
        assertThat(response.getCompanyId()).isEqualTo(3);
        verify(claimService).complete(any(), any());
    }

    @Test
    void blankCodeThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> authService.loginWithGithub(new GithubLoginRequest("")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
