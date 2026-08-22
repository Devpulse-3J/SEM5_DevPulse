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
                SELECT system_role FROM users WHERE company_id = ? AND user_id = ?
                """, (rs, rowNum) -> rs.getString("system_role"), companyId, userId)
                .stream().findFirst();
    }

    /** True when the project exists and belongs to the given company. */
    public boolean projectExistsInCompany(Integer companyId, Integer projectId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM projects WHERE company_id = ? AND project_id = ?
                """, Integer.class, companyId, projectId);
        return count != null && count > 0;
    }
}
