package com.devpulse.notification.controller;

import com.devpulse.notification.entity.AlertRule;
import com.devpulse.notification.service.AlertRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController

@RequestMapping("/alerts/rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @GetMapping
    public ResponseEntity<List<AlertRule>> getRules(@RequestParam(defaultValue = "1") Integer companyId) {
        List<AlertRule> rules = alertRuleService.getRulesByCompany(companyId);
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRuleById(@PathVariable("id") Integer ruleId) {
        return alertRuleService.getRuleById(ruleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AlertRule> createRule(@RequestBody AlertRule rule) {
        AlertRule created = alertRuleService.createRule(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable("id") Integer ruleId) {
        boolean deleted = alertRuleService.deleteRule(ruleId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
