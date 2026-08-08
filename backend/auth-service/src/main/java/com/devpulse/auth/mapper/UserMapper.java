package com.devpulse.auth.mapper;

import com.devpulse.auth.dto.AuthResponse;
import com.devpulse.auth.dto.UserProfileResponse;
import com.devpulse.auth.dto.UserProfileResponse.ProjectRoleEntry;
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
     * Converts a {@link User} entity and JWT token metadata into an {@link AuthResponse}.
     */
    public AuthResponse toAuthResponse(User user, String token, long expiresIn) {
        return new AuthResponse(
                token,
                expiresIn,
                user.getUserId(),
                user.getEmail(),
                user.getFullName(),
                user.getSystemRole()
        );
    }

    /**
     * Converts a {@link User} entity and associated {@link ProjectMember} memberships into a {@link UserProfileResponse}.
     */
    public UserProfileResponse toUserProfileResponse(User user, List<ProjectMember> memberships) {
        List<ProjectRoleEntry> projectRoles = memberships.stream()
                .map(this::toProjectRoleEntry)
                .collect(Collectors.toList());

        UserProfileResponse profile = new UserProfileResponse();
        profile.setUserId(user.getUserId());
        profile.setEmail(user.getEmail());
        profile.setFullName(user.getFullName());
        profile.setSystemRole(user.getSystemRole());
        profile.setCompanyId(user.getCompany() != null ? user.getCompany().getCompanyId() : null);
        profile.setCompanyName(user.getCompany() != null ? user.getCompany().getCompanyName() : null);
        profile.setProjectRoles(projectRoles);

        return profile;
    }

    /**
     * Converts a {@link ProjectMember} into a {@link ProjectRoleEntry}.
     */
    public ProjectRoleEntry toProjectRoleEntry(ProjectMember member) {
        return new ProjectRoleEntry(member.getProjectId(), member.getRole());
    }
}
