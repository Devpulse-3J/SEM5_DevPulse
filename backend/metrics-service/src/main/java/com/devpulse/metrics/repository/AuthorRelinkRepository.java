package com.devpulse.metrics.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Attributes pull requests that arrived with no author to the user whose GitHub
 * account opened them.
 *
 * <p>A PR row does not keep its author's GitHub id, so it is recovered from the
 * raw webhook/sync payload that created it (raw_event_log, a table this service
 * may read). A payload identifies its PR by repository id + number - the number
 * alone is not enough, since a company can have several repos - and carries the
 * author under {@code pull_request.user} (live webhook) or {@code user} (history
 * sync).
 *
 * <p>Only rows with {@code author_id IS NULL} are touched, so it never
 * reassigns a PR that already has an author and is safe to run repeatedly.
 */
@Repository
public class AuthorRelinkRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthorRelinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> findGithubId(Integer userId) {
        return jdbcTemplate.query("""
                SELECT github_id FROM users WHERE user_id = ? AND github_id IS NOT NULL
                """, (rs, rowNum) -> rs.getLong("github_id"), userId)
                .stream().findFirst();
    }

    /** @return how many pull requests were attributed */
    public int relinkPullRequests(Integer companyId, Integer userId, long githubId) {
        return jdbcTemplate.update("""
                UPDATE pull_requests pr
                   SET author_id = ?
                  FROM repos r
                 WHERE r.repo_id = pr.repo_id
                   AND pr.company_id = ?
                   AND pr.author_id IS NULL
                   AND EXISTS (
                        SELECT 1 FROM raw_event_log e
                         WHERE e.company_id = pr.company_id
                           AND e.provider = 'github'
                           AND e.event_type = 'pull_request'
                           AND COALESCE(e.payload->'pull_request'->>'number', e.payload->>'number')
                               = CAST(pr.github_pr_number AS text)
                           AND COALESCE(e.payload->'repository'->>'id', e.payload->'base'->'repo'->>'id')
                               = CAST(r.github_repo_id AS text)
                           AND COALESCE(e.payload->'pull_request'->'user'->>'id', e.payload->'user'->>'id')
                               = CAST(? AS text))
                """, userId, companyId, githubId);
    }
}
