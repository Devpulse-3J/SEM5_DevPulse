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

    public Optional<AlertRule> getRuleById(Integer ruleId) {
        return alertRuleRepository.findById(ruleId);
    }

    public AlertRule createRule(AlertRule rule) {
        return alertRuleRepository.save(rule);
    }

    public boolean deleteRule(Integer ruleId) {
        Optional<AlertRule> existing = alertRuleRepository.findById(ruleId);
        if (existing.isPresent()) {
            AlertRule rule = existing.get();
            rule.setActive(false);
            alertRuleRepository.save(rule);
            return true;
        }
        return false;
    }
}
