package com.devpulse.integration.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only lookups against tables integration-service does not own.
 *
 * <p>{@code users} and {@code projects} belong to auth-service. The ownership
 * rule is "a service may READ any table it needs, but WRITE only the tables it
 * owns" — these are reads, and nothing here issues an INSERT, UPDATE or DELETE.
 *
 * <p>Plain JDBC rather than JPA entities on purpose: mapping {@code User} and
 * {@code Project} entities here would invite someone to save one.
 */
@Repository
public class TenantAccessRepository {

    private final JdbcTemplate jdbcTemplate;

    public TenantAccessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The caller's company-level role, or empty if the user does not belong to
     * that company. The gateway forwards no role claim, so this is the only way
     * this service can tell an admin from a developer.
     */
    public Optional<String> findSystemRole(Integer companyId, Integer userId) {
        return jdbcTemplate.query("""
                SELECT role FROM (
                    SELECT role, 0 AS priority FROM company_members WHERE company_id = ? AND user_id = ?
                    UNION ALL
                    SELECT system_role AS role, 1 AS priority FROM users WHERE company_id = ? AND user_id = ?
                ) r ORDER BY priority LIMIT 1
                """, (rs, rowNum) -> rs.getString("role"), companyId, userId, companyId, userId)
                .stream().findFirst();
    }

    /** True when the project exists and belongs to the given company. */
    public boolean projectExistsInCompany(Integer companyId, Integer projectId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM projects WHERE company_id = ? AND project_id = ?
                """, Integer.class, companyId, projectId);
        return count != null && count > 0;
    }

    /** The internal project a Jira project key was linked to via Create/Edit Project, if any. */
    public Optional<Integer> findProjectIdByJiraKey(Integer companyId, String jiraProjectKey) {
        if (jiraProjectKey == null || jiraProjectKey.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT project_id FROM projects WHERE company_id = ? AND jira_project_key = ?
                """, (rs, rowNum) -> rs.getInt("project_id"), companyId, jiraProjectKey)
                .stream().findFirst();
    }

    /**
     * The DevPulse user whose email matches a Jira assignee's email address.
     * Jira Cloud identifies assignees by an opaque accountId with no DevPulse
     * equivalent, so email is the only reliable link between the two systems.
     */
    public Optional<Integer> findUserIdByEmail(Integer companyId, String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT user_id FROM users WHERE company_id = ? AND lower(email) = lower(?)
                """, (rs, rowNum) -> rs.getInt("user_id"), companyId, email)
                .stream().findFirst();
    }
}
