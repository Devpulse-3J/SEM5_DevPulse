package com.devpulse.auth.mapper;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.dto.UserProfileResponse;
import com.devpulse.auth.dto.UserProfileResponse.CompanyEntry;
import com.devpulse.auth.dto.UserProfileResponse.ProjectRoleEntry;
import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.ProjectMember;
import com.devpulse.auth.entity.User;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Mapper component responsible for transforming JPA entities into API DTOs.
 * Enforces Single Responsibility by keeping entity-to-DTO conversion out of service classes.
 */
@Component
public class UserMapper {

    /**
     * Converts a {@link User} entity and JWT token metadata into an {@link AuthResponse}
     * scoped to the user's home company/role.
     */
    public AuthResponse toAuthResponse(User user, String token, long expiresIn) {
        Integer homeCompanyId = user.getCompany() != null ? user.getCompany().getCompanyId() : null;
        return toAuthResponse(user, token, expiresIn, homeCompanyId, user.getSystemRole());
    }

    /**
     * Same as {@link #toAuthResponse(User, String, long)}, but scoped to an
     * explicit company/role rather than the user's home company — used after
     * {@code /auth/companies/{id}/switch}, where the active company is not
     * {@code user.getCompany()}.
     */
    public AuthResponse toAuthResponse(User user, String token, long expiresIn,
                                        Integer companyId, String roleInCompany) {
        return new AuthResponse(
                token,
                expiresIn,
                user.getUserId(),
                user.getEmail(),
                user.getFullName(),
                roleInCompany,
                companyId
        );
    }

    /**
     * Converts a {@link User} entity and associated {@link ProjectMember} memberships into a {@link UserProfileResponse}.
     * Scoped to the user's home company/role, with no company or project detail.
     */
    public UserProfileResponse toUserProfileResponse(User user, List<ProjectMember> memberships) {
        List<ProjectRoleEntry> projectRoles = memberships.stream()
                .map(this::toProjectRoleEntry)
                .collect(Collectors.toList());
        return toUserProfileResponse(user, user.getCompany(), user.getSystemRole(), projectRoles, List.of());
    }

    /**
     * Profile scoped to an explicit active company and the caller's role in it,
     * with per-project company detail and every company the user belongs to.
     */
    public UserProfileResponse toUserProfileResponse(User user, Company activeCompany, String activeRole,
                                                     List<ProjectRoleEntry> projectRoles,
                                                     List<CompanyEntry> companies) {
        UserProfileResponse profile = new UserProfileResponse();
        profile.setUserId(user.getUserId());
        profile.setEmail(user.getEmail());
        profile.setFullName(user.getFullName());
        profile.setSystemRole(activeRole);
        profile.setCompanyId(activeCompany != null ? activeCompany.getCompanyId() : null);
        profile.setCompanyName(activeCompany != null ? activeCompany.getCompanyName() : null);
        profile.setProjectRoles(projectRoles);
        profile.setCompanies(companies);

        return profile;
    }

    /**
     * Converts a {@link ProjectMember} into a {@link ProjectRoleEntry}.
     */
    public ProjectRoleEntry toProjectRoleEntry(ProjectMember member) {
        return new ProjectRoleEntry(member.getProjectId(), member.getRole());
    }
}
