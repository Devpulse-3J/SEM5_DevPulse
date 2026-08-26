package com.devpulse.integration.security;

import com.devpulse.integration.exception.ApiException;
import com.devpulse.integration.repository.TenantAccessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Role and tenancy checks for the project-scoped GitHub endpoints.
 *
 * <p>Both are needed and are easy to conflate: proving the caller is an admin
 * says nothing about whether the target project is theirs. Every method here
 * does both, in that order.
 */
@Service
public class ProjectAccessService {

    private static final String ADMIN_ROLE = "admin";

    private final TenantAccessRepository tenantAccessRepository;

    public ProjectAccessService(TenantAccessRepository tenantAccessRepository) {
        this.tenantAccessRepository = tenantAccessRepository;
    }

    /**
     * Admin-only access to one project.
     *
     * <p>Linking, checking and syncing a repository are all admin actions —
     * managers and developers must not be able to repoint a project at a
     * different repository.
     */
    public void requireAdminOnProject(RequestContext context, Integer projectId) {
        String systemRole = tenantAccessRepository
                .findSystemRole(context.companyId(), context.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "The authenticated user does not belong to this company"));

        if (!ADMIN_ROLE.equalsIgnoreCase(systemRole)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Only company admins can manage a project's GitHub connection");
        }

        // 404 rather than 403 for another tenant's project: confirming the id
        // exists would itself leak across companies.
        if (!tenantAccessRepository.projectExistsInCompany(context.companyId(), projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No project with id " + projectId);
        }
    }
}
