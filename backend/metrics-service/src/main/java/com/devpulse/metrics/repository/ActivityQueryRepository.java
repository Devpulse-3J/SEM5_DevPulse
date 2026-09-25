package com.devpulse.metrics.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActivityQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ActivityQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PullRequestRow> findPullRequests(
            Integer companyId, Integer projectId, Integer authorId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT pr.pr_id, pr.github_pr_number, pr.title, pr.description,
                       pr.author_id, u.full_name AS author_name, u.avatar_url AS author_avatar,
                       r.repo_id, r.repo_name, pr.is_draft, pr.state, pr.head_branch,
                       pr.base_branch, pr.lines_added, pr.lines_deleted, pr.files_changed,
                       pr.url, pr.created_at, pr.updated_at, pr.merged_at
                FROM pull_requests pr
                JOIN repos r ON r.repo_id = pr.repo_id AND r.company_id = pr.company_id
                LEFT JOIN users u ON u.user_id = pr.author_id AND (u.company_id = pr.company_id OR EXISTS (SELECT 1 FROM company_members cm WHERE cm.user_id = u.user_id AND cm.company_id = pr.company_id))
                WHERE pr.company_id = :companyId
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("limit", limit)
                .addValue("offset", offset);
        if (projectId != null) {
            sql.append(" AND r.project_id = :projectId");
            parameters.addValue("projectId", projectId);
        }
        if (authorId != null) {
            sql.append(" AND pr.author_id = :authorId");
            parameters.addValue("authorId", authorId);
        }
        sql.append(" ORDER BY pr.created_at DESC LIMIT :limit OFFSET :offset");
        return jdbcTemplate.query(sql.toString(), parameters,
                (rs, rowNum) -> new PullRequestRow(
                        rs.getInt("pr_id"),
                        rs.getInt("github_pr_number"),
                        rs.getString("title"),
                        rs.getString("description"),
                        nullableInteger(rs, "author_id"),
                        rs.getString("author_name"),
                        rs.getString("author_avatar"),
                        rs.getInt("repo_id"),
                        rs.getString("repo_name"),
                        rs.getBoolean("is_draft"),
                        rs.getString("state"),
                        rs.getString("head_branch"),
                        rs.getString("base_branch"),
                        rs.getInt("lines_added"),
                        rs.getInt("lines_deleted"),
                        rs.getInt("files_changed"),
                        rs.getString("url"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at"),
                        instant(rs, "merged_at")));
    }

    public List<ReviewRow> findReviews(List<Integer> prIds) {
        if (prIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT rv.review_id, rv.pr_id, rv.review_state, rv.reviewed_at,
                       u.full_name AS reviewer_name, u.avatar_url AS reviewer_avatar
                FROM pr_reviews rv
                LEFT JOIN users u ON u.user_id = rv.reviewer_id AND (u.company_id = rv.company_id OR EXISTS (SELECT 1 FROM company_members cm WHERE cm.user_id = u.user_id AND cm.company_id = rv.company_id))
                WHERE rv.pr_id IN (:prIds)
                ORDER BY rv.reviewed_at
                """, Map.of("prIds", prIds), (rs, rowNum) -> new ReviewRow(
                        rs.getInt("review_id"),
                        rs.getInt("pr_id"),
                        rs.getString("reviewer_name"),
                        rs.getString("reviewer_avatar"),
                        rs.getString("review_state"),
                        instant(rs, "reviewed_at")));
    }

    /**
     * The most recent prediction for each of the given PRs. A PR can be scored
     * more than once (a new model version, a manual re-score), and only the
     * latest is meaningful. pr_predictions is owned by analytics-service; this
     * only reads it.
     */
    public List<PredictionRow> findLatestPredictions(Integer companyId, List<Integer> prIds) {
        if (prIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT ranked.pr_id, ranked.risk_score, ranked.risk_category,
                       ranked.algorithm, ranked.model_version, ranked.predicted_at
                FROM (
                    SELECT p.pr_id, p.risk_score, p.risk_category, p.algorithm,
                           p.model_version, p.predicted_at,
                           ROW_NUMBER() OVER (
                               PARTITION BY p.pr_id
                               ORDER BY p.predicted_at DESC NULLS LAST, p.prediction_id DESC
                           ) AS rn
                    FROM pr_predictions p
                    WHERE p.company_id = :companyId AND p.pr_id IN (:prIds)
                ) ranked
                WHERE ranked.rn = 1
                """, Map.of("companyId", companyId, "prIds", prIds),
                (rs, rowNum) -> new PredictionRow(
                        rs.getInt("pr_id"),
                        rs.getBigDecimal("risk_score").doubleValue(),
                        rs.getString("risk_category"),
                        rs.getString("algorithm"),
                        rs.getString("model_version"),
                        instant(rs, "predicted_at")));
    }

    public List<CheckRow> findChecks(List<Integer> prIds) {
        if (prIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT check_id, pr_id, name, status, url
                FROM pr_checks
                WHERE pr_id IN (:prIds)
                ORDER BY check_id
                """, Map.of("prIds", prIds), (rs, rowNum) -> new CheckRow(
                        rs.getInt("check_id"),
                        rs.getInt("pr_id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getString("url")));
    }

    public List<DeploymentRow> findDeployments(
            Integer companyId, Integer projectId, String environment, String status,
            int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.deployment_id, d.github_deployment_id, d.commit_sha,
                       d.environment, d.status, d.deployed_at, d.failure_recovered_at,
                       d.triggered_by_user_id, u.full_name AS triggered_by_name,
                       c.commit_time
                FROM deployments d
                LEFT JOIN users u
                  ON u.user_id = d.triggered_by_user_id AND (u.company_id = d.company_id OR EXISTS (SELECT 1 FROM company_members cm WHERE cm.user_id = u.user_id AND cm.company_id = d.company_id))
                LEFT JOIN commits c
                  ON c.commit_sha = d.commit_sha AND c.company_id = d.company_id
                WHERE d.company_id = :companyId AND d.project_id = :projectId
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("projectId", projectId)
                .addValue("limit", limit)
                .addValue("offset", offset);
        if (environment != null) {
            sql.append(" AND d.environment = :environment");
            parameters.addValue("environment", environment);
        }
        if (status != null) {
            sql.append(" AND d.status = :status");
            parameters.addValue("status", status);
        }
        sql.append(" ORDER BY d.deployed_at DESC LIMIT :limit OFFSET :offset");
        return jdbcTemplate.query(sql.toString(), parameters, (rs, rowNum) -> new DeploymentRow(
                rs.getInt("deployment_id"),
                nullableLong(rs, "github_deployment_id"),
                rs.getString("commit_sha"),
                rs.getString("environment"),
                rs.getString("status"),
                instant(rs, "deployed_at"),
                instant(rs, "failure_recovered_at"),
                nullableInteger(rs, "triggered_by_user_id"),
                rs.getString("triggered_by_name"),
                instant(rs, "commit_time")));
    }

    public List<MemberRow> findProjectMembers(Integer companyId, Integer projectId) {
        return jdbcTemplate.query("""
                SELECT u.user_id, u.full_name
                FROM project_members pm
                JOIN users u ON u.user_id = pm.user_id
                WHERE pm.project_id = :projectId AND (u.company_id = :companyId OR EXISTS (SELECT 1 FROM company_members cm WHERE cm.user_id = u.user_id AND cm.company_id = :companyId))
                ORDER BY u.full_name
                """, Map.of("companyId", companyId, "projectId", projectId),
                (rs, rowNum) -> new MemberRow(rs.getInt("user_id"), rs.getString("full_name")));
    }

    public List<PullRequestCycleFact> findPullRequestCycleFacts(
            Integer companyId, Integer projectId, Instant cycleWindowStart) {
        return jdbcTemplate.query("""
                SELECT pr.author_id, pr.state, pr.created_at, pr.merged_at
                FROM pull_requests pr
                JOIN repos r ON r.repo_id = pr.repo_id AND r.company_id = pr.company_id
                WHERE pr.company_id = :companyId AND r.project_id = :projectId
                  AND (pr.state = 'open' OR pr.merged_at >= :cycleWindowStart)
                """, new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("projectId", projectId)
                        .addValue("cycleWindowStart", Timestamp.from(cycleWindowStart)),
                (rs, rowNum) -> new PullRequestCycleFact(
                        nullableInteger(rs, "author_id"),
                        rs.getString("state"),
                        instant(rs, "created_at"),
                        instant(rs, "merged_at")));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record PullRequestRow(
            Integer id, int number, String title, String description,
            Integer authorId, String authorName, String authorAvatar,
            Integer repositoryId, String repositoryName, boolean draft, String state,
            String headBranch, String baseBranch, int additions, int deletions,
            int changedFiles, String url, Instant createdAt, Instant updatedAt, Instant mergedAt) {
    }

    /** risk_score is the model's probability, 0 to 1. risk_category is low, medium or high. */
    public record PredictionRow(
            Integer prId, double riskScore, String riskCategory, String algorithm,
            String modelVersion, Instant predictedAt) {
    }

    public record ReviewRow(
            Integer id, Integer prId, String reviewerName, String reviewerAvatar,
            String state, Instant submittedAt) {
    }

    public record CheckRow(Integer id, Integer prId, String name, String status, String url) {
    }

    public record DeploymentRow(
            Integer id, Long githubDeploymentId, String commitSha, String environment,
            String status, Instant deployedAt, Instant failureRecoveredAt,
            Integer triggeredByUserId, String triggeredByName, Instant commitTime) {
    }

    public record MemberRow(Integer userId, String name) {
    }

    public record PullRequestCycleFact(
            Integer authorId, String state, Instant createdAt, Instant mergedAt) {
    }
}
