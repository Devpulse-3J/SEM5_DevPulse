package com.devpulse.notification.repository;

import com.devpulse.notification.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Integer> {
    List<AlertRule> findByCompanyIdAndIsActiveTrue(Integer companyId);
    List<AlertRule> findByCompanyIdAndProjectIdAndIsActiveTrue(Integer companyId, Integer projectId);
}
