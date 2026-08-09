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

    // -- constructors --------------------------------------------------------

    public UserProfileResponse() {
    }

    // -- nested DTO for per-project roles ------------------------------------

    public static class ProjectRoleEntry {
        private Integer projectId;
        private String role;

        public ProjectRoleEntry() {
        }

        public ProjectRoleEntry(Integer projectId, String role) {
            this.projectId = projectId;
            this.role = role;
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
}
