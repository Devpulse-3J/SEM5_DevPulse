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

        AlertRule created = alertRuleService.createRule(rule);

        assertNotNull(created);
        verify(alertRuleRepository, times(1)).save(rule);
    }

    @Test
    void testDeleteRuleSuccess() {
        AlertRule rule = new AlertRule(1, 10, "stale_pr", 48, "#dev-alerts", 1);
        when(alertRuleRepository.findById(5)).thenReturn(Optional.of(rule));

        boolean result = alertRuleService.deleteRule(5);

        assertTrue(result);
        assertFalse(rule.isActive());
        verify(alertRuleRepository, times(1)).save(rule);
    }
}
