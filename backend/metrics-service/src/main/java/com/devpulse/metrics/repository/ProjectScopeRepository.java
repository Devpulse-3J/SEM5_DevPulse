package com.devpulse.metrics.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectScopeRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectScopeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ProjectScope> findProject(Integer companyId, Integer projectId) {
        List<ProjectScope> rows = jdbcTemplate.query("""
                SELECT p.project_id, p.company_id, p.project_name, COUNT(r.repo_id) AS repo_count
                FROM projects p
                LEFT JOIN repos r ON r.project_id = p.project_id AND r.company_id = p.company_id
                WHERE p.company_id = ? AND p.project_id = ?
                GROUP BY p.project_id, p.company_id, p.project_name
                """, (rs, rowNum) -> new ProjectScope(
                        rs.getInt("project_id"),
                        rs.getInt("company_id"),
                        rs.getString("project_name"),
                        rs.getLong("repo_count")), companyId, projectId);
        return rows.stream().findFirst();
    }

    /**
     * The caller's role in {@code companyId} - not necessarily their home company.
     * A user can belong to several companies (company_members), so the role is
     * resolved for the company the request is scoped to; the legacy
     * users.company_id/system_role row is only a fallback for accounts that
     * predate company_members.
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

    public boolean isProjectMember(Integer projectId, Integer userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM project_members WHERE project_id = ? AND user_id = ?
                """, Integer.class, projectId, userId);
        return count != null && count > 0;
    }

    public List<ProjectScope> findAllProjects() {
        return jdbcTemplate.query("""
                SELECT p.project_id, p.company_id, p.project_name, COUNT(r.repo_id) AS repo_count
                FROM projects p
                LEFT JOIN repos r ON r.project_id = p.project_id AND r.company_id = p.company_id
                GROUP BY p.project_id, p.company_id, p.project_name
                ORDER BY p.project_id
                """, (rs, rowNum) -> new ProjectScope(
                        rs.getInt("project_id"),
                        rs.getInt("company_id"),
                        rs.getString("project_name"),
                        rs.getLong("repo_count")));
    }

    public record ProjectScope(Integer projectId, Integer companyId, String projectName, long repoCount) {
    }
}
