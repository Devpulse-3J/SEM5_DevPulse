package com.devpulse.notification.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Read-only lookups against tables notification-service does not own
 * ({@code repos}, {@code commits}, {@code projects}, {@code project_members},
 * {@code users}). Plain JDBC rather than entities so nothing here can write them.
 */
@Repository
public class ProjectDirectoryRepository {

    public record DeployedProject(Integer projectId, String projectName, String repoFullName) {
    }

    public record Manager(Integer userId, String email, String fullName) {
    }

    private static final RowMapper<DeployedProject> PROJECT_MAPPER = (rs, rowNum) -> new DeployedProject(
            rs.getInt("project_id"), rs.getString("project_name"), rs.getString("full_name"));

    private final JdbcTemplate jdbcTemplate;

    public ProjectDirectoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The project a deployment belongs to. A deployment event carries GitHub's
     * repository id in its projectId field, not a DevPulse project id, so the
     * project is found through the linked repo, or failing that through the
     * deployed commit.
     */
    public Optional<DeployedProject> findProjectForDeployment(Integer companyId, Integer githubRepoId, String commitSha) {
        if (githubRepoId != null) {
            Optional<DeployedProject> byRepo = jdbcTemplate.query("""
                    SELECT p.project_id, p.project_name, r.full_name
                    FROM repos r JOIN projects p ON p.project_id = r.project_id
                    WHERE r.company_id = ? AND r.github_repo_id = ?
                    """, PROJECT_MAPPER, companyId, githubRepoId.longValue()).stream().findFirst();
            if (byRepo.isPresent()) {
                return byRepo;
            }
        }
        if (commitSha == null || commitSha.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT p.project_id, p.project_name, r.full_name
                FROM commits c
                JOIN repos r ON r.repo_id = c.repo_id AND r.company_id = c.company_id
                JOIN projects p ON p.project_id = r.project_id
                WHERE c.company_id = ? AND c.commit_sha = ?
                """, PROJECT_MAPPER, companyId, commitSha).stream().findFirst();
    }

    /** Members holding the manager role on the project who have an email address. */
    public List<Manager> findManagers(Integer projectId) {
        return jdbcTemplate.query("""
                SELECT u.user_id, u.email, u.full_name
                FROM project_members pm JOIN users u ON u.user_id = pm.user_id
                WHERE pm.project_id = ? AND pm.role = 'manager'
                  AND u.email IS NOT NULL AND u.email <> ''
                """, (rs, rowNum) -> new Manager(rs.getInt("user_id"), rs.getString("email"), rs.getString("full_name")),
                projectId);
    }
}
