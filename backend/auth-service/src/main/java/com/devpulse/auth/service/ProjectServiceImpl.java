package com.devpulse.auth.service;

import com.devpulse.auth.dto.CreateProjectRequest;
import com.devpulse.auth.dto.ProjectResponse;
import com.devpulse.auth.dto.UpdateProjectRequest;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.Project;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ConflictException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.repository.CompanyRepository;
import com.devpulse.auth.repository.ProjectMemberRepository;
import com.devpulse.auth.repository.ProjectRepository;
import com.devpulse.auth.security.ProjectAccessService;
import com.devpulse.auth.security.RequestContext;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectServiceImpl implements ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceImpl.class);
    private static final String ADMIN_ROLE = "admin";

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final CompanyRepository companyRepository;
    private final ProjectAccessService projectAccessService;

    public ProjectServiceImpl(ProjectRepository projectRepository,
                              ProjectMemberRepository projectMemberRepository,
                              CompanyRepository companyRepository,
                              ProjectAccessService projectAccessService) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.companyRepository = companyRepository;
        this.projectAccessService = projectAccessService;
    }

    @Override
    @Transactional
    public ProjectResponse create(RequestContext context, CreateProjectRequest request) {
        User admin = projectAccessService.requireAdmin(context);

        String projectName = request.getProjectName().trim();
        if (projectRepository.existsByCompanyCompanyIdAndProjectNameIgnoreCase(
                context.companyId(), projectName)) {
            throw new ConflictException(
                    "A project named '" + projectName + "' already exists in this company");
        }

        Company company = companyRepository.findById(context.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", context.companyId()));

        Project project = new Project(
                company,
                projectName,
                blankToNull(request.getDescription()),
                blankToNull(request.getJiraProjectKey()),
                blankToNull(request.getGithubRepoUrl()));

        Project saved = projectRepository.save(project);
        log.info("Admin {} created project {} ({}) in company {}",
                admin.getEmail(), saved.getProjectId(), projectName, context.companyId());
        return ProjectResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> list(RequestContext context) {
        User caller = projectAccessService.requireCaller(context);
        List<Project> projects = projectRepository.findByCompanyCompanyId(context.companyId());

        if (!ADMIN_ROLE.equalsIgnoreCase(caller.getSystemRole())) {
            // A non-admin sees only what they are a member of. Filtering here
            // rather than in SQL keeps the company predicate in one place; a
            // company's project count is small enough that it does not matter.
            Set<Integer> visible = projectMemberRepository
                    .findByUserId(caller.getUserId()).stream()
                    .map(ProjectMember::getProjectId)
                    .collect(Collectors.toSet());
            projects = projects.stream()
                    .filter(project -> visible.contains(project.getProjectId()))
                    .toList();
        }

        return projects.stream()
                .sorted(Comparator.comparing(Project::getProjectId))
                .map(ProjectResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse get(RequestContext context, Integer projectId) {
        return ProjectResponse.from(
                projectAccessService.requireProjectVisible(context, projectId));
    }

    @Override
    @Transactional
    public ProjectResponse update(RequestContext context, Integer projectId,
                                  UpdateProjectRequest request) {
        User admin = projectAccessService.requireAdmin(context);
        Project project = projectAccessService.requireProjectInCompany(context, projectId);

        String projectName = request.getProjectName().trim();
        // Renaming onto another project's name collides; keeping your own name
        // must not.
        if (!projectName.equalsIgnoreCase(project.getProjectName())
                && projectRepository.existsByCompanyCompanyIdAndProjectNameIgnoreCase(
                        context.companyId(), projectName)) {
            throw new ConflictException(
                    "A project named '" + projectName + "' already exists in this company");
        }

        project.setProjectName(projectName);
        project.setDescription(blankToNull(request.getDescription()));
        project.setJiraProjectKey(blankToNull(request.getJiraProjectKey()));
        project.setGithubRepoUrl(blankToNull(request.getGithubRepoUrl()));

        Project saved = projectRepository.save(project);
        log.info("Admin {} updated project {} in company {}",
                admin.getEmail(), projectId, context.companyId());
        return ProjectResponse.from(saved);
    }

    @Override
    @Transactional
    public void delete(RequestContext context, Integer projectId) {
        User admin = projectAccessService.requireAdmin(context);
        Project project = projectAccessService.requireProjectInCompany(context, projectId);

        // Memberships go first. The FK is ON DELETE CASCADE, but Hibernate does
        // not know that, and relying on it would leave the persistence context
        // holding rows the database has already removed.
        projectMemberRepository.deleteByProjectId(projectId);
        projectRepository.delete(project);

        // repos.project_id is ON DELETE SET NULL, so any linked repository
        // survives as an orphan row rather than taking its pull requests and
        // commits with it. Relinking it is integration-service's job.
        log.info("Admin {} deleted project {} in company {}; any linked repo is now unassigned",
                admin.getEmail(), projectId, context.companyId());
    }

    /** Treats "" and "   " as absent, so an emptied form field clears the column. */
    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
