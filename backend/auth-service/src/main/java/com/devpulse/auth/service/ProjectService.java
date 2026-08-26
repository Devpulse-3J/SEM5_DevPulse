package com.devpulse.auth.service;

import com.devpulse.auth.dto.CreateProjectRequest;
import com.devpulse.auth.dto.ProjectResponse;
import com.devpulse.auth.dto.UpdateProjectRequest;
import com.devpulse.auth.security.RequestContext;
import java.util.List;

/**
 * Project CRUD, scoped to the caller's company.
 *
 * <p>Every method takes the {@link RequestContext} rather than an id, so no
 * caller can pass a company other than its own.
 */
public interface ProjectService {

    /** Admin only. */
    ProjectResponse create(RequestContext context, CreateProjectRequest request);

    /**
     * Admins get every project in the company; everyone else gets only the
     * projects they are a member of.
     */
    List<ProjectResponse> list(RequestContext context);

    /** Admin, or a member of the project. */
    ProjectResponse get(RequestContext context, Integer projectId);

    /** Admin only. */
    ProjectResponse update(RequestContext context, Integer projectId, UpdateProjectRequest request);

    /** Admin only. */
    void delete(RequestContext context, Integer projectId);
}
