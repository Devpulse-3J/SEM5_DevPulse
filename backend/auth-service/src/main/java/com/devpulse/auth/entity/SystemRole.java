package com.devpulse.auth.entity;

/**
 * Company-level role stored in {@code users.system_role}.
 * <p>
 * An Admin has company-wide authority (create projects, invite members, manage
 * integrations). A Member has no elevated company-level privileges — their
 * capabilities come from per-project roles.
 */
public enum SystemRole {
    ADMIN,
    MEMBER,
    DEVELOPER,
    MANAGER;

    /**
     * Returns the lowercase value used in the database CHECK constraint.
     */
    public String toDbValue() {
        return name().toLowerCase();
    }

    public static SystemRole fromDbValue(String value) {
        return SystemRole.valueOf(value.toUpperCase());
    }
}
