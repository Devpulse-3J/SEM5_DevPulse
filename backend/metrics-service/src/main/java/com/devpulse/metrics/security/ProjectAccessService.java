package com.devpulse.metrics.security;

import com.devpulse.metrics.exception.ApiException;
import com.devpulse.metrics.repository.ProjectScopeRepository;
import com.devpulse.metrics.repository.ProjectScopeRepository.ProjectScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {

    private final ProjectScopeRepository projectScopeRepository;

    public ProjectAccessService(ProjectScopeRepository projectScopeRepository) {
        this.projectScopeRepository = projectScopeRepository;
    }

    public ProjectScope requireViewAccess(RequestContext context, Integer projectId) {
        ProjectScope project = projectScopeRepository.findProject(context.companyId(), projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "No project with id " + projectId));
        String systemRole = projectScopeRepository.findSystemRole(context.companyId(), context.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_CONTEXT_NOT_FOUND",
                        "The authenticated user does not belong to this company"));
        if (!"admin".equalsIgnoreCase(systemRole)
                && !projectScopeRepository.isProjectMember(projectId, context.userId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED",
                    "The authenticated user is not a member of this project");
        }
        return project;
    }
}
