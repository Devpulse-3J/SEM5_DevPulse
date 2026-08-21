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

    public Optional<String> findSystemRole(Integer companyId, Integer userId) {
        return jdbcTemplate.query("""
                SELECT system_role FROM users WHERE company_id = ? AND user_id = ?
                """, (rs, rowNum) -> rs.getString("system_role"), companyId, userId)
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
