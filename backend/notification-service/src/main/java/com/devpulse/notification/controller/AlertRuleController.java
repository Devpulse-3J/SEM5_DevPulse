package com.devpulse.notification.controller;

import com.devpulse.notification.entity.AlertRule;
import com.devpulse.notification.service.AlertAccessService;
import com.devpulse.notification.service.AlertRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alerts/rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;
    private final AlertAccessService alertAccessService;

    public AlertRuleController(AlertRuleService alertRuleService, AlertAccessService alertAccessService) {
        this.alertRuleService = alertRuleService;
        this.alertAccessService = alertAccessService;
    }

    @GetMapping
    public ResponseEntity<List<AlertRule>> getRules(
            @RequestHeader("X-Company-Id") Integer companyId,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        alertAccessService.requireManagerOrAdmin(companyId, userId);
        List<AlertRule> rules = alertRuleService.getRulesByCompany(companyId);
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRuleById(@PathVariable("id") Integer ruleId,
                                         @RequestHeader("X-Company-Id") Integer companyId,
                                         @RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        alertAccessService.requireManagerOrAdmin(companyId, userId);
        return alertRuleService.getRuleById(ruleId, companyId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AlertRule> createRule(@RequestBody AlertRule rule,
                                                @RequestHeader("X-Company-Id") Integer companyId,
                                                @RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        alertAccessService.requireManagerOrAdmin(companyId, userId);
        AlertRule created = alertRuleService.createRule(rule, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable("id") Integer ruleId,
                                        @RequestHeader("X-Company-Id") Integer companyId,
                                        @RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        alertAccessService.requireManagerOrAdmin(companyId, userId);
        boolean deleted = alertRuleService.deleteRule(ruleId, companyId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}