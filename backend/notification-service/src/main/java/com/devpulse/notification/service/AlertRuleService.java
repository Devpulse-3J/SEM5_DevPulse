package com.devpulse.notification.service;

import com.devpulse.notification.entity.AlertRule;
import com.devpulse.notification.repository.AlertRuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;

    public AlertRuleService(AlertRuleRepository alertRuleRepository) {
        this.alertRuleRepository = alertRuleRepository;
    }

    public List<AlertRule> getRulesByCompany(Integer companyId) {
        return alertRuleRepository.findByCompanyIdAndIsActiveTrue(companyId);
    }

    /**
     * Every lookup below is scoped to the caller's company. Using the bare
     * {@code findById} here previously let any authenticated user read, delete
     * or overwrite another tenant's rule by guessing a numeric id.
     */
    public Optional<AlertRule> getRuleById(Integer ruleId, Integer companyId) {
        return alertRuleRepository.findByRuleIdAndCompanyId(ruleId, companyId);
    }

    public AlertRule createRule(AlertRule rule, Integer companyId) {
        // The company is taken from the authenticated request, never from the
        // request body — otherwise a caller could create rules for a tenant
        // they do not belong to.
        rule.setCompanyId(companyId);
        return alertRuleRepository.save(rule);
    }

    public boolean deleteRule(Integer ruleId, Integer companyId) {
        Optional<AlertRule> existing =
                alertRuleRepository.findByRuleIdAndCompanyId(ruleId, companyId);
        if (existing.isPresent()) {
            AlertRule rule = existing.get();
            rule.setActive(false);
            alertRuleRepository.save(rule);
            return true;
        }
        return false;
    }
}
