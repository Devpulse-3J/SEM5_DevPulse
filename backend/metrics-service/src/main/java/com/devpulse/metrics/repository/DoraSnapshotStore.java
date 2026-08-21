package com.devpulse.metrics.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DoraSnapshotStore {

    private final JdbcTemplate jdbcTemplate;

    public DoraSnapshotStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(Snapshot snapshot) {
        jdbcTemplate.update("""
                INSERT INTO dora_metrics (
                    company_id, project_id, calculated_date, calculated_at, window_days,
                    deployment_frequency, lead_time_hours, change_failure_rate, mttr_hours)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (project_id, calculated_date, window_days) DO UPDATE SET
                    company_id = EXCLUDED.company_id,
                    calculated_at = EXCLUDED.calculated_at,
                    deployment_frequency = EXCLUDED.deployment_frequency,
                    lead_time_hours = EXCLUDED.lead_time_hours,
                    change_failure_rate = EXCLUDED.change_failure_rate,
                    mttr_hours = EXCLUDED.mttr_hours
                """,
                snapshot.companyId(), snapshot.projectId(), snapshot.calculatedDate(),
                Timestamp.from(snapshot.calculatedAt()), snapshot.windowDays(),
                snapshot.deploymentFrequency(), snapshot.leadTimeHours(),
                snapshot.changeFailureRate(), snapshot.mttrHours());
    }

    public List<Snapshot> findHistory(
            Integer companyId, Integer projectId, int windowDays, LocalDate since) {
        return jdbcTemplate.query("""
                SELECT company_id, project_id, calculated_date, calculated_at, window_days,
                       deployment_frequency, lead_time_hours, change_failure_rate, mttr_hours
                FROM dora_metrics
                WHERE company_id = ? AND project_id = ? AND window_days = ?
                  AND calculated_date >= ?
                ORDER BY calculated_date
                """, (rs, rowNum) -> new Snapshot(
                        rs.getInt("company_id"),
                        rs.getInt("project_id"),
                        rs.getObject("calculated_date", LocalDate.class),
                        rs.getTimestamp("calculated_at").toInstant(),
                        rs.getInt("window_days"),
                        rs.getBigDecimal("deployment_frequency"),
                        rs.getBigDecimal("lead_time_hours"),
                        rs.getBigDecimal("change_failure_rate"),
                        rs.getBigDecimal("mttr_hours")),
                companyId, projectId, windowDays, since);
    }

    public record Snapshot(
            Integer companyId,
            Integer projectId,
            LocalDate calculatedDate,
            Instant calculatedAt,
            int windowDays,
            BigDecimal deploymentFrequency,
            BigDecimal leadTimeHours,
            BigDecimal changeFailureRate,
            BigDecimal mttrHours) {
    }
}
