package com.devpulse.auth.dto;

import java.util.List;

/**
 * Response body for {@code GET /auth/me}.
 * Returns the authenticated user's profile including all per-project role
 * memberships.
 */
public class UserProfileResponse {

    private Integer userId;
    private String email;
    private String fullName;
    private String systemRole;
    private Integer companyId;
    private String companyName;
    private List<ProjectRoleEntry> projectRoles;
    private List<CompanyEntry> companies;
    /** The linked GitHub account's numeric id, or null when none is linked. */
    private Long githubId;

    // -- constructors --------------------------------------------------------

    public UserProfileResponse() {
    }

    // -- nested DTO for per-project roles ------------------------------------

    public static class ProjectRoleEntry {
        private Integer projectId;
        private String role;
        // Which company the project lives in. A user can hold project roles in
        // several companies, but a token is scoped to one, so the client needs
        // this to know when it must switch company before opening a project.
        private Integer companyId;
        private String companyName;
        private String projectName;

        public ProjectRoleEntry() {
        }

        public ProjectRoleEntry(Integer projectId, String role) {
            this.projectId = projectId;
            this.role = role;
        }

        public ProjectRoleEntry(Integer projectId, String role, Integer companyId,
                                String companyName, String projectName) {
            this.projectId = projectId;
            this.role = role;
            this.companyId = companyId;
            this.companyName = companyName;
            this.projectName = projectName;
        }

        public Integer getCompanyId() {
            return companyId;
        }

        public void setCompanyId(Integer companyId) {
            this.companyId = companyId;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public Integer getProjectId() {
            return projectId;
        }

        public void setProjectId(Integer projectId) {
            this.projectId = projectId;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    /** A company the user belongs to, and their role there ({@code admin} or {@code member}). */
    public static class CompanyEntry {
        private Integer companyId;
        private String companyName;
        private String role;

        public CompanyEntry() {
        }

        public CompanyEntry(Integer companyId, String companyName, String role) {
            this.companyId = companyId;
            this.companyName = companyName;
            this.role = role;
        }

        public Integer getCompanyId() {
            return companyId;
        }

        public void setCompanyId(Integer companyId) {
            this.companyId = companyId;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    // -- getters / setters ---------------------------------------------------

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
    }

    public Integer getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Integer companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public List<ProjectRoleEntry> getProjectRoles() {
        return projectRoles;
    }

    public void setProjectRoles(List<ProjectRoleEntry> projectRoles) {
        this.projectRoles = projectRoles;
    }

    public List<CompanyEntry> getCompanies() {
        return companies;
    }

    public void setCompanies(List<CompanyEntry> companies) {
        this.companies = companies;
    }

    public Long getGithubId() {
        return githubId;
    }

    public void setGithubId(Long githubId) {
        this.githubId = githubId;
    }
}
