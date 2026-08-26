package com.devpulse.auth.security;

import com.devpulse.auth.entity.Project;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ForbiddenException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.exception.UnauthorizedException;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.ProjectRepository;
import com.devpulse.auth.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place every project endpoint resolves "may this caller do this?".
 *
 * <p>Two checks are always needed and are easy to conflate:
 * <ol>
 *   <li><b>Role</b> — is the caller an admin? Company-wide, from
 *       {@code users.system_role}.</li>
 *   <li><b>Tenancy</b> — does the target project belong to the caller's
 *       company?</li>
 * </ol>
 * Checking only the first is the bug the audit found in
 * {@code WorkspaceInviteServiceImpl.inviteToProject}: a company-A admin could
 * act on any project id in any company. Every method here does both.
 */
@Service
public class ProjectAccessService {

    private static final String ADMIN_ROLE = "admin";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectAccessService(UserRepository userRepository,
                                ProjectRepository projectRepository,
                                ProjectMemberRepository projectMemberRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    /**
     * Loads the caller named by the gateway headers.
     *
     * <p>Also cross-checks the header identity against the JWT principal Spring
     * Security already authenticated. They come from the same token via
     * different routes, so a mismatch means something between the gateway and
     * here rewrote a header — never a legitimate request.
     */
    @Transactional(readOnly = true)
    public User requireCaller(RequestContext context) {
        User caller = userRepository.findById(context.userId())
                .orElseThrow(() -> new UnauthorizedException(
                        "The authenticated user no longer exists"));

        if (caller.getCompany() == null
                || !caller.getCompany().getCompanyId().equals(context.companyId())) {
            throw new UnauthorizedException(
                    "The authenticated user does not belong to the named company");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User principal
                && !principal.getUserId().equals(caller.getUserId())) {
            throw new UnauthorizedException("Identity headers do not match the bearer token");
        }

        return caller;
    }

    /** Company-scoped admin. Managers and developers never pass this. */
    @Transactional(readOnly = true)
    public User requireAdmin(RequestContext context) {
        User caller = requireCaller(context);
        if (!ADMIN_ROLE.equalsIgnoreCase(caller.getSystemRole())) {
            throw new ForbiddenException("Only company admins can perform this action");
        }
        return caller;
    }

    /**
     * The project, if it exists <em>in the caller's company</em>.
     *
     * <p>A project belonging to another company is reported as 404 rather than
     * 403 — confirming the id exists would itself leak across tenants.
     */
    @Transactional(readOnly = true)
    public Project requireProjectInCompany(RequestContext context, Integer projectId) {
        return projectRepository
                .findByProjectIdAndCompanyCompanyId(projectId, context.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    /** Admin-only action on a project: both checks, in order. */
    @Transactional(readOnly = true)
    public Project requireAdminOnProject(RequestContext context, Integer projectId) {
        requireAdmin(context);
        return requireProjectInCompany(context, projectId);
    }

    /**
     * Read access: an admin sees every project in the company, anyone else only
     * the projects they are a member of.
     */
    @Transactional(readOnly = true)
    public Project requireProjectVisible(RequestContext context, Integer projectId) {
        User caller = requireCaller(context);
        Project project = requireProjectInCompany(context, projectId);

        if (ADMIN_ROLE.equalsIgnoreCase(caller.getSystemRole())) {
            return project;
        }
        if (projectMemberRepository
                .findByProjectIdAndUserId(projectId, caller.getUserId())
                .isEmpty()) {
            throw new ForbiddenException("You are not a member of this project");
        }
        return project;
    }
}
