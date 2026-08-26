package com.devpulse.notification.repository;

import com.devpulse.notification.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Integer> {
    List<AlertRule> findByCompanyIdAndIsActiveTrue(Integer companyId);
    List<AlertRule> findByCompanyIdAndProjectIdAndIsActiveTrue(Integer companyId, Integer projectId);

    /**
     * Tenant-scoped lookup by id. Prefer this over {@code findById}: a bare
     * findById lets any caller read or delete another company's rule just by
     * guessing an id.
     */
    Optional<AlertRule> findByRuleIdAndCompanyId(Integer ruleId, Integer companyId);
}
