package com.devpulse.notification.service;

import com.devpulse.notification.entity.AlertRule;
import com.devpulse.notification.repository.AlertRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @InjectMocks
    private AlertRuleService alertRuleService;

    @Test
    void testGetRulesByCompany() {
        AlertRule rule = new AlertRule(1, 10, "stale_pr", 48, "#dev-alerts", 1);
        when(alertRuleRepository.findByCompanyIdAndIsActiveTrue(1)).thenReturn(List.of(rule));

        List<AlertRule> results = alertRuleService.getRulesByCompany(1);

        assertEquals(1, results.size());
        assertEquals("stale_pr", results.get(0).getRuleType());
    }

    @Test
    void testCreateRule() {
        AlertRule rule = new AlertRule(1, 10, "stale_pr", 48, "#dev-alerts", 1);
        when(alertRuleRepository.save(rule)).thenReturn(rule);

        AlertRule created = alertRuleService.createRule(rule, 1);

        assertNotNull(created);
        verify(alertRuleRepository, times(1)).save(rule);
    }

    /**
     * The company on a new rule comes from the authenticated request, not the
     * request body — otherwise a caller could create rules inside another
     * tenant simply by setting companyId in the JSON.
     */
    @Test
    void testCreateRuleOverwritesBodySuppliedCompanyId() {
        AlertRule rule = new AlertRule(99, 10, "stale_pr", 48, "#dev-alerts", 1);
        when(alertRuleRepository.save(rule)).thenReturn(rule);

        alertRuleService.createRule(rule, 1);

        assertEquals(1, rule.getCompanyId());
    }

    @Test
    void testDeleteRuleSuccess() {
        AlertRule rule = new AlertRule(1, 10, "stale_pr", 48, "#dev-alerts", 1);
        when(alertRuleRepository.findByRuleIdAndCompanyId(5, 1)).thenReturn(Optional.of(rule));

        boolean result = alertRuleService.deleteRule(5, 1);

        assertTrue(result);
        assertFalse(rule.isActive());
        verify(alertRuleRepository, times(1)).save(rule);
    }

    /** A rule owned by another company must not be reachable for deletion. */
    @Test
    void testDeleteRuleFromAnotherCompanyFails() {
        when(alertRuleRepository.findByRuleIdAndCompanyId(5, 99)).thenReturn(Optional.empty());

        boolean result = alertRuleService.deleteRule(5, 99);

        assertFalse(result);
        verify(alertRuleRepository, never()).save(any(AlertRule.class));
    }

    @Test
    void testGetRuleByIdIsTenantScoped() {
        when(alertRuleRepository.findByRuleIdAndCompanyId(5, 99)).thenReturn(Optional.empty());

        assertTrue(alertRuleService.getRuleById(5, 99).isEmpty());
        // The unscoped findById must never be used for id lookups.
        verify(alertRuleRepository, never()).findById(anyInt());
    }
}
