package com.devpulse.metrics.repository;

import com.devpulse.metrics.domain.DeploymentFact;
import com.devpulse.metrics.domain.DeploymentStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DoraQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public DoraQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DeploymentFact> findProductionFacts(
            Integer companyId, Integer projectId, Instant startInclusive, Instant endExclusive) {
        return jdbcTemplate.query("""
                SELECT d.status, d.deployed_at, d.failure_recovered_at, c.commit_time
                FROM deployments d
                LEFT JOIN commits c
                  ON c.commit_sha = d.commit_sha AND c.company_id = d.company_id
                WHERE d.company_id = ?
                  AND d.project_id = ?
                  AND d.environment = 'production'
                  AND d.deployed_at >= ?
                  AND d.deployed_at < ?
                ORDER BY d.deployed_at
                """, (rs, rowNum) -> new DeploymentFact(
                        DeploymentStatus.fromDatabase(rs.getString("status")),
                        toInstant(rs.getObject("deployed_at")),
                        toInstant(rs.getObject("failure_recovered_at")),
                        toInstant(rs.getObject("commit_time"))),
                companyId, projectId, Timestamp.from(startInclusive), Timestamp.from(endExclusive));
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("Unsupported timestamp value: " + value.getClass());
    }
}
