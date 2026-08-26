package com.devpulse.notification.controller;

import com.devpulse.notification.entity.AlertRule;
import com.devpulse.notification.service.AlertRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertRuleController.class)
class AlertRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertRuleService alertRuleService;

    // The tenant now arrives as the X-Company-Id header, which the gateway
    // populates from validated JWT claims. It used to be a ?companyId= query
    // parameter that any caller could set.
    private static final String COMPANY_HEADER = "X-Company-Id";

    @Test
    void testGetRules() throws Exception {
        AlertRule rule = new AlertRule(1, 10, "stale_pr", 48, "#dev-alerts", 1);
        when(alertRuleService.getRulesByCompany(1)).thenReturn(List.of(rule));

        mockMvc.perform(get("/alerts/rules").header(COMPANY_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleType").value("stale_pr"));
    }

    @Test
    void testGetRuleByIdFound() throws Exception {
        AlertRule rule = new AlertRule(1, 10, "stale_pr", 48, "#dev-alerts", 1);
        when(alertRuleService.getRuleById(1, 1)).thenReturn(Optional.of(rule));

        mockMvc.perform(get("/alerts/rules/1").header(COMPANY_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleType").value("stale_pr"));
    }

    /**
     * A rule that exists but belongs to another company must look exactly like
     * one that does not exist — 404, never 403, so the id is not confirmed.
     */
    @Test
    void testGetRuleByIdFromAnotherCompanyReturns404() throws Exception {
        when(alertRuleService.getRuleById(1, 99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/alerts/rules/1").header(COMPANY_HEADER, "99"))
                .andExpect(status().isNotFound());
    }

    /** Without the gateway-supplied header there is no tenant to scope to. */
    @Test
    void testGetRulesWithoutCompanyHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/alerts/rules"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateRule() throws Exception {
        AlertRule rule = new AlertRule(1, 10, "stale_pr", 48, "#dev-alerts", 1);
        when(alertRuleService.createRule(any(AlertRule.class), any(Integer.class))).thenReturn(rule);

        String jsonBody = """
            {
                "companyId": 1,
                "projectId": 10,
                "ruleType": "stale_pr",
                "thresholdHours": 48,
                "slackChannel": "#dev-alerts",
                "createdByUserId": 1
            }
            """;

        mockMvc.perform(post("/alerts/rules")
                        .header(COMPANY_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleType").value("stale_pr"));
    }

    @Test
    void testDeleteRuleFromAnotherCompanyReturns404() throws Exception {
        when(alertRuleService.deleteRule(1, 99)).thenReturn(false);

        mockMvc.perform(delete("/alerts/rules/1").header(COMPANY_HEADER, "99"))
                .andExpect(status().isNotFound());
    }
}
