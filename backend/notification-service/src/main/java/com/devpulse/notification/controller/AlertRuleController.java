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

    /**
     * The tenant comes from {@code X-Company-Id}, which the API gateway strips
     * from the incoming request and re-adds from validated JWT claims — so it
     * cannot be forged by a client. It was previously a request parameter
     * defaulting to 1, which let any caller read any company's rules with
     * {@code ?companyId=N}.
     */
    @GetMapping
    public ResponseEntity<List<AlertRule>> getRules(
            @RequestHeader("X-Company-Id") Integer companyId) {
        List<AlertRule> rules = alertRuleService.getRulesByCompany(companyId);
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRuleById(@PathVariable("id") Integer ruleId,
                                         @RequestHeader("X-Company-Id") Integer companyId) {
        // A rule belonging to another company returns 404, not 403: revealing
        // that the id exists would itself leak information across tenants.
        return alertRuleService.getRuleById(ruleId, companyId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AlertRule> createRule(@RequestBody AlertRule rule,
                                                @RequestHeader("X-Company-Id") Integer companyId) {
        AlertRule created = alertRuleService.createRule(rule, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable("id") Integer ruleId,
                                        @RequestHeader("X-Company-Id") Integer companyId) {
        boolean deleted = alertRuleService.deleteRule(ruleId, companyId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
