package com.devpulse.auth.service;

import com.devpulse.auth.dto.UserProfileResponse;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.CompanyMember;
import com.devpulse.auth.entity.Project;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.mapper.UserMapper;
import com.devpulse.auth.repository.CompanyMemberRepository;
import com.devpulse.auth.repository.CompanyRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.ProjectRepository;
import com.devpulse.auth.repository.UserRepository;
import com.devpulse.auth.security.JwtService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@code GET /auth/me} for someone who belongs to more than one company.
 *
 * <p>The user here mirrors the real case that surfaced the gap: admin of their
 * home company (fcode) and a plain member of a second (Odin_eye) with a
 * developer role on a project there. The profile must describe the company the
 * token is scoped to, and tell the client which company each project is in -
 * otherwise it cannot know it must switch company before opening that project.
 */
public class AuthServiceProfileTest {

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
