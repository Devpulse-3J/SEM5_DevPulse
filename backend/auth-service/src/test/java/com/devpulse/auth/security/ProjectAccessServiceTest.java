package com.devpulse.auth.security;

import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.CompanyMember;
import com.devpulse.auth.entity.Project;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ForbiddenException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.exception.UnauthorizedException;
import com.devpulse.auth.repository.CompanyMemberRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.ProjectRepository;
import com.devpulse.auth.repository.UserRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the two checks every project endpoint relies on: tenancy (does this
 * project belong to the company named by the request?) and role (is the
 * caller an admin <em>in that company</em>?).
 *
 * <p>Both are now resolved through {@code company_members} instead of
 * {@code users.company_id}/{@code system_role}, so a token issued by
 * {@code /auth/companies/{id}/switch} is honoured. The single-company case
 * (every user today) must behave exactly as before: their one
 * {@code company_members} row mirrors {@code users.company_id}/{@code
 * system_role} via the V11 backfill and every write since.
 */
public class ProjectAccessServiceTest {

    private static final Integer COMPANY_A = 6;
    private static final Integer COMPANY_B = 9;
    private static final Integer PROJECT_ID = 3;

    private UserRepository userRepository;
    private ProjectRepository projectRepository;
    private ProjectMemberRepository projectMemberRepository;
    private CompanyMemberRepository companyMemberRepository;
    private ProjectAccessService service;

    private User user;
    private Project projectInA;

    @BeforeEach
    public void setUp() {
        userRepository = mock(UserRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        companyMemberRepository = mock(CompanyMemberRepository.class);

        service = new ProjectAccessService(userRepository, projectRepository,
                projectMemberRepository, companyMemberRepository);

        user = new User();
        user.setUserId(20);
        user.setEmail("dev@example.com");

        Company companyA = new Company();
        companyA.setCompanyId(COMPANY_A);
        projectInA = newProject(PROJECT_ID, companyA);

        when(userRepository.findById(20)).thenReturn(Optional.of(user));
    }

    private static Project newProject(Integer projectId, Company company) {
        Project project = new Project();
        try {
            setField(project, "projectId", projectId);
            setField(project, "company", company);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return project;
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    // -- requireCaller ---------------------------------------------------------

    @Test
    public void requireCallerSucceedsWhenACompanyMembersRowExistsForTheContextCompany() {
        RequestContext context = new RequestContext(20, COMPANY_A);
        when(companyMemberRepository.findByUserIdAndCompanyId(20, COMPANY_A))
                .thenReturn(Optional.of(new CompanyMember(20, COMPANY_A, "member")));

        User result = service.requireCaller(context);

        assertSame(user, result);
    }

    @Test
    public void requireCallerRejectsACompanyTheUserHasNoMembershipRowFor() {
        RequestContext context = new RequestContext(20, COMPANY_B);
        when(companyMemberRepository.findByUserIdAndCompanyId(20, COMPANY_B))
                .thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> service.requireCaller(context));
    }

    @Test
    public void requireCallerSucceedsForASwitchedTokenNamingASecondCompany() {
        // Home company is A; the caller switched into B, where they are a
        // plain member. This is exactly what /auth/companies/{id}/switch
        // enables and must not be rejected as "does not belong".
        RequestContext context = new RequestContext(20, COMPANY_B);
        when(companyMemberRepository.findByUserIdAndCompanyId(20, COMPANY_B))
                .thenReturn(Optional.of(new CompanyMember(20, COMPANY_B, "member")));

        assertDoesNotThrow(() -> service.requireCaller(context));
    }

    // -- requireAdmin ------------------------------------------------------------

    @Test
    public void requireAdminSucceedsWhenTheCompanyMembersRoleIsAdmin() {
        RequestContext context = new RequestContext(20, COMPANY_A);
        when(companyMemberRepository.findByUserIdAndCompanyId(20, COMPANY_A))
                .thenReturn(Optional.of(new CompanyMember(20, COMPANY_A, "admin")));

        assertDoesNotThrow(() -> service.requireAdmin(context));
    }

    @Test
    public void requireAdminRejectsAMemberRole() {
        RequestContext context = new RequestContext(20, COMPANY_A);
        when(companyMemberRepository.findByUserIdAndCompanyId(20, COMPANY_A))
                .thenReturn(Optional.of(new CompanyMember(20, COMPANY_A, "member")));

        assertThrows(ForbiddenException.class, () -> service.requireAdmin(context));
    }

    @Test
    public void anAdminOfTheirHomeCompanyIsNotAnAdminAfterSwitchingToASecondCompany() {
        // The one-admin-per-user constraint means a company-A admin can only
        // ever be a 'member' row elsewhere. requireAdmin must resolve role
        // from the ACTIVE company, never from users.system_role, or this
        // admin's global role would wrongly follow them into company B.
        RequestContext context = new RequestContext(20, COMPANY_B);
        when(companyMemberRepository.findByUserIdAndCompanyId(20, COMPANY_B))
                .thenReturn(Optional.of(new CompanyMember(20, COMPANY_B, "member")));

        assertThrows(ForbiddenException.class, () -> service.requireAdmin(context));
    }

    // -- requireProjectInCompany -------------------------------------------------

    @Test
    public void requireProjectInCompanyReturns404ForAProjectInAnotherCompany() {
        RequestContext context = new RequestContext(20, COMPANY_B);
        when(projectRepository.findByProjectIdAndCompanyCompanyId(PROJECT_ID, COMPANY_B))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireProjectInCompany(context, PROJECT_ID));
    }

    // -- requireProjectVisible ----------------------------------------------------

    @Test
    public void anAdminSeesAProjectTheyAreNotDirectlyAMemberOf() {
        RequestContext context = new RequestContext(20, COMPANY_A);
        when(companyMemberRepository.findByUserIdAndCompanyId(20, COMPANY_A))
                .thenReturn(Optional.of(new CompanyMember(20, COMPANY_A, "admin")));
        when(projectRepository.findByProjectIdAndCompanyCompanyId(PROJECT_ID, COMPANY_A))
                .thenReturn(Optional.of(projectInA));

        Project result = service.requireProjectVisible(context, PROJECT_ID);

        assertSame(projectInA, result);
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    public void aNonAdminMustBeAProjectMemberToSeeIt() {
        RequestContext context = new RequestContext(20, COMPANY_A);
        when(companyMemberRepository.findByUserIdAndCompanyId(20, COMPANY_A))
                .thenReturn(Optional.of(new CompanyMember(20, COMPANY_A, "member")));
        when(projectRepository.findByProjectIdAndCompanyCompanyId(PROJECT_ID, COMPANY_A))
                .thenReturn(Optional.of(projectInA));
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, 20))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> service.requireProjectVisible(context, PROJECT_ID));
    }

    @Test
    public void aNonAdminWhoIsAProjectMemberSeesIt() {
        RequestContext context = new RequestContext(20, COMPANY_A);
        when(companyMemberRepository.findByUserIdAndCompanyId(20, COMPANY_A))
                .thenReturn(Optional.of(new CompanyMember(20, COMPANY_A, "member")));
        when(projectRepository.findByProjectIdAndCompanyCompanyId(PROJECT_ID, COMPANY_A))
                .thenReturn(Optional.of(projectInA));
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, 20))
                .thenReturn(Optional.of(new ProjectMember(PROJECT_ID, 20, "developer")));

        Project result = service.requireProjectVisible(context, PROJECT_ID);

        assertSame(projectInA, result);
    }

    @Test
    public void anAdminOfCompanyACannotUseThatStatusToSeeAProjectInCompanyB() {
        // Regression guard for the exact bug called out in the class doc:
        // admin status must be scoped to the company named in the request,
        // not carried in from wherever the caller happens to be admin.
        RequestContext context = new RequestContext(20, COMPANY_B);
        Company companyB = new Company();
        companyB.setCompanyId(COMPANY_B);
        Project projectInB = newProject(11, companyB);

        when(companyMemberRepository.findByUserIdAndCompanyId(20, COMPANY_B))
                .thenReturn(Optional.of(new CompanyMember(20, COMPANY_B, "member")));
        when(projectRepository.findByProjectIdAndCompanyCompanyId(11, COMPANY_B))
                .thenReturn(Optional.of(projectInB));
        when(projectMemberRepository.findByProjectIdAndUserId(11, 20))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> service.requireProjectVisible(context, 11));
    }
}
