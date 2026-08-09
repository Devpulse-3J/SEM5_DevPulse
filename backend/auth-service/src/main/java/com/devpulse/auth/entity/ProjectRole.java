package com.devpulse.auth.entity;

/**
 * Per-project role stored in {@code project_members.role}.
 * <p>
 * A Manager owns a specific project (team management, alert config, dashboards).
 * A Developer contributes code to the project and views their own activity.
 * The same user can hold different roles on different projects.
 */
public enum ProjectRole {
    MANAGER,
    DEVELOPER;

    /**
     * Returns the lowercase value used in the database CHECK constraint.
     */
    public String toDbValue() {
        return name().toLowerCase();
    }

    public static ProjectRole fromDbValue(String value) {
        return ProjectRole.valueOf(value.toUpperCase());
    }
}
