package com.devpulse.metrics.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventReferenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public EventReferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RepoReference> resolveRepo(Integer companyId, Integer eventRepoId) {
        List<RepoReference> byExternalId = jdbcTemplate.query("""
                SELECT repo_id, company_id, project_id, github_repo_id
                FROM repos WHERE company_id = ? AND github_repo_id = ?
                """, ROW_MAPPER, companyId, eventRepoId.longValue());
        if (!byExternalId.isEmpty()) {
            return Optional.of(byExternalId.get(0));
        }
        return jdbcTemplate.query("""
                SELECT repo_id, company_id, project_id, github_repo_id
                FROM repos WHERE company_id = ? AND repo_id = ?
                """, ROW_MAPPER, companyId, eventRepoId).stream().findFirst();
    }

    public boolean commitExistsForCompany(String commitSha, Integer companyId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM commits WHERE commit_sha = ? AND company_id = ?
                """, Integer.class, commitSha, companyId);
        return count != null && count > 0;
    }

    public Optional<Integer> resolveUserId(Integer companyId, Integer eventUserId) {
        return resolveUserId(companyId, eventUserId, null);
    }

    /**
     * Maps a GitHub author (id and/or email) to a user of {@code companyId}.
     * "A user of the company" means a company_members row OR the legacy home
     * company: a person can belong to several companies, so requiring
     * users.company_id to match left every cross-company member's PRs and
     * commits with no author.
     */
    public Optional<Integer> resolveUserId(Integer companyId, Integer eventUserId, String email) {
        if (eventUserId != null) {
            List<Integer> byGithubId = jdbcTemplate.query("""
                    SELECT u.user_id FROM users u WHERE (u.company_id = ? OR EXISTS (SELECT 1 FROM company_members cm WHERE cm.user_id = u.user_id AND cm.company_id = ?)) AND u.github_id = ?
                    """, (rs, rowNum) -> rs.getInt("user_id"), companyId, companyId, eventUserId.longValue());
            if (!byGithubId.isEmpty()) {
                return Optional.of(byGithubId.get(0));
            }
        }
        if (email != null && !email.isBlank()) {
            List<Integer> byEmail = jdbcTemplate.query("""
                    SELECT u.user_id FROM users u WHERE (u.company_id = ? OR EXISTS (SELECT 1 FROM company_members cm WHERE cm.user_id = u.user_id AND cm.company_id = ?)) AND LOWER(u.email) = LOWER(?)
                    """, (rs, rowNum) -> rs.getInt("user_id"), companyId, companyId, email.trim());
            if (!byEmail.isEmpty()) {
                Integer userId = byEmail.get(0);
                if (eventUserId != null) {
                    jdbcTemplate.update("""
                            UPDATE users SET github_id = ? WHERE user_id = ? AND github_id IS NULL
                            """, eventUserId.longValue(), userId);
                }
                return Optional.of(userId);
            }
        }
        if (eventUserId != null) {
            return jdbcTemplate.query("""
                    SELECT u.user_id FROM users u WHERE (u.company_id = ? OR EXISTS (SELECT 1 FROM company_members cm WHERE cm.user_id = u.user_id AND cm.company_id = ?)) AND u.user_id = ?
                    """, (rs, rowNum) -> rs.getInt("user_id"), companyId, companyId, eventUserId)
                    .stream().findFirst();
        }
        return Optional.empty();
    }

    public Optional<Integer> resolveDeploymentProject(
            Integer companyId, Integer eventProjectId, String commitSha) {
        if (commitSha != null && !commitSha.isBlank()) {
            List<Integer> fromCommit = jdbcTemplate.query("""
                    SELECT r.project_id
                    FROM commits c
                    JOIN repos r ON r.repo_id = c.repo_id AND r.company_id = c.company_id
                    WHERE c.company_id = ? AND c.commit_sha = ? AND r.project_id IS NOT NULL
                    """, (rs, rowNum) -> rs.getInt("project_id"), companyId, commitSha);
            if (!fromCommit.isEmpty()) {
                return Optional.of(fromCommit.get(0));
            }
        }
        if (eventProjectId != null) {
            List<Integer> direct = jdbcTemplate.query("""
                    SELECT project_id FROM projects WHERE company_id = ? AND project_id = ?
                    """, (rs, rowNum) -> rs.getInt("project_id"), companyId, eventProjectId);
            if (!direct.isEmpty()) {
                return Optional.of(direct.get(0));
            }
            List<Integer> fromRepo = jdbcTemplate.query("""
                    SELECT project_id FROM repos
                    WHERE company_id = ? AND (github_repo_id = ? OR repo_id = ?) AND project_id IS NOT NULL
                    """, (rs, rowNum) -> rs.getInt("project_id"), companyId, eventProjectId.longValue(), eventProjectId);
            if (!fromRepo.isEmpty()) {
                return Optional.of(fromRepo.get(0));
            }
        }
        return jdbcTemplate.query("""
                SELECT project_id FROM repos
                WHERE company_id = ? AND project_id IS NOT NULL
                """, (rs, rowNum) -> rs.getInt("project_id"), companyId)
                .stream().findFirst();
    }

    private static final org.springframework.jdbc.core.RowMapper<RepoReference> ROW_MAPPER =
            (rs, rowNum) -> new RepoReference(
                    rs.getInt("repo_id"),
                    rs.getInt("company_id"),
                    (Integer) rs.getObject("project_id"),
                    rs.getLong("github_repo_id"));

    public record RepoReference(Integer repoId, Integer companyId, Integer projectId, Long githubRepoId) {
    }
}
