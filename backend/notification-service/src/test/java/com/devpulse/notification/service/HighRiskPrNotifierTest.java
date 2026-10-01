package com.devpulse.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.devpulse.contracts.events.AlertPrHighRiskEvent;
import com.devpulse.notification.email.EmailNotificationService;
import com.devpulse.notification.entity.Alert;
import com.devpulse.notification.entity.AlertRule;
import com.devpulse.notification.entity.Notification;
import com.devpulse.notification.repository.AlertRepository;
import com.devpulse.notification.repository.AlertRuleRepository;
import com.devpulse.notification.repository.NotificationRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository.Manager;
import com.devpulse.notification.slack.SlackNotificationService;
import com.devpulse.notification.repository.ProjectDirectoryRepository.ProjectPullRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.dao.DataIntegrityViolationException;

class HighRiskPrNotifierTest {

    private static final int COMPANY = 15;
    private static final int PROJECT = 8;
    private static final int PR_ID = 412;

    private AlertRuleRepository alertRuleRepository;
    private ProjectDirectoryRepository projectDirectory;
    private AlertRepository alertRepository;
    private NotificationRepository notificationRepository;
    private SlackNotificationService slackService;
    private EmailNotificationService emailService;
    private HighRiskPrNotifier notifier;

    @BeforeEach
    void setUp() {
        alertRuleRepository = mock(AlertRuleRepository.class);
        projectDirectory = mock(ProjectDirectoryRepository.class);
        alertRepository = mock(AlertRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        slackService = mock(SlackNotificationService.class);
        emailService = mock(EmailNotificationService.class);
        notifier = new HighRiskPrNotifier(alertRuleRepository, projectDirectory, new PrAlertDispatcher(
                projectDirectory, alertRepository, notificationRepository, slackService, emailService));

        pullRequestIs("open");
        when(projectDirectory.findManagers(PROJECT)).thenReturn(List.of(new Manager(40, "manager@example.com", "Mana Ger")));
        when(alertRepository.saveAndFlush(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setAlertId(77);
            return alert;
        });
        when(slackService.sendSlackNotification(anyString(), anyString())).thenReturn(true);
        when(emailService.sendEmailNotification(anyString(), anyString(), anyString())).thenReturn(true);
    }

    private void pullRequestIs(String state) {
        when(projectDirectory.findPullRequest(COMPANY, PR_ID)).thenReturn(Optional.of(new ProjectPullRequest(
                PR_ID, 63, "Rework login", null, state, Instant.parse("2026-10-01T05:00:00Z"),
                PROJECT, "Dev_pulse_Backend", "Devpulse-3J/SEM5_DevPulse")));
    }

    private static AlertRule rule(int id, Integer projectId, String type, String channel) {
        AlertRule rule = new AlertRule(COMPANY, projectId, type, null, channel, 40);
        rule.setRuleId(id);
        return rule;
    }

    private void activeRules(AlertRule... rules) {
        when(alertRuleRepository.findByCompanyIdAndIsActiveTrue(COMPANY)).thenReturn(List.of(rules));
    }

    /** The event exactly as the ML service sends it: no project, no timestamp. */
    private static AlertPrHighRiskEvent event() {
        AlertPrHighRiskEvent event = new AlertPrHighRiskEvent();
        event.setEventType("alert.pr_high_risk");
        event.setCompanyId(COMPANY);
        event.setPrId(PR_ID);
        event.setRiskScore(0.8312);
        event.setRiskCategory("high");
        return event;
    }

    @Test
    void theMlServicesMessageDeserialisesIntoTheEvent() throws Exception {
        // The exact body and header analytics-service publishes (app/consumers/pr_events.py).
        byte[] body = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "eventType", "alert.pr_high_risk", "companyId", COMPANY, "prId", PR_ID,
                "riskScore", 0.8312, "riskCategory", "high", "modelVersion", "1.0.0"))
                .getBytes(StandardCharsets.UTF_8);
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setHeader("__TypeId__", "com.devpulse.contracts.events.AlertPrHighRiskEvent");

        Object converted = new Jackson2JsonMessageConverter().fromMessage(new Message(body, properties));

        AlertPrHighRiskEvent event = assertInstanceOf(AlertPrHighRiskEvent.class, converted);
        assertEquals(PR_ID, event.getPrId());
        assertEquals(COMPANY, event.getCompanyId());
        assertEquals(0.8312, event.getRiskScore());
    }

    @Test
    void postsToTheRulesChannelAndEmailsTheProjectsManager() {
        activeRules(rule(5, PROJECT, "HIGH_RISK_PR", "#risk"));

        notifier.handle(event());

        ArgumentCaptor<String> slackText = ArgumentCaptor.forClass(String.class);
        verify(slackService).sendSlackNotification(eq("#risk"), slackText.capture());
        assertTrue(slackText.getValue().contains("#63 Rework login"));
        assertTrue(slackText.getValue().contains("risk score 0.83"));
        assertTrue(slackText.getValue().contains("https://github.com/Devpulse-3J/SEM5_DevPulse/pull/63"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailNotification(eq("manager@example.com"),
                eq("High risk pull request in Dev_pulse_Backend (#63)"), body.capture());
        assertTrue(body.getValue().contains("Risk score:   0.83 (high)"));

        ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).saveAndFlush(alert.capture());
        assertEquals(PROJECT, alert.getValue().getProjectId());
        assertEquals(5, alert.getValue().getRuleId());
        assertEquals(PR_ID, alert.getValue().getEntityId());
        assertEquals("pr-high-risk:" + PR_ID, alert.getValue().getDedupKey());

        ArgumentCaptor<Notification> notifications = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(notifications.capture());
        assertEquals(List.of("slack", "email"), notifications.getAllValues().stream().map(Notification::getChannel).toList());
        assertEquals(40, notifications.getAllValues().get(1).getUserId());
    }

    @Test
    void postsToEveryMatchingRulesChannelButEmailsOnce() {
        activeRules(rule(5, PROJECT, "HIGH_RISK_PR", "#risk"), rule(6, null, "high_risk_pr", "#company-wide"),
                rule(7, null, "HIGH_RISK_PR", "#risk"));

        notifier.handle(event());

        verify(slackService).sendSlackNotification(eq("#risk"), anyString());
        verify(slackService).sendSlackNotification(eq("#company-wide"), anyString());
        verify(emailService, times(1)).sendEmailNotification(anyString(), anyString(), anyString());
        verify(alertRepository, times(1)).saveAndFlush(any(Alert.class));
    }

    @Test
    void raisesNothingWhenOnlyOtherRuleTypesOrOtherProjectsAreConfigured() {
        activeRules(rule(2, null, "STALE_PR", "#eng-alert"), rule(9, 20, "HIGH_RISK_PR", "#frontend"));

        notifier.handle(event());

        verifyNoInteractions(alertRepository, slackService, emailService, notificationRepository);
    }

    @Test
    void alertsOnceWhenTheSamePullRequestIsScoredAgain() {
        activeRules(rule(5, PROJECT, "HIGH_RISK_PR", "#risk"));
        when(alertRepository.existsByDedupKey("pr-high-risk:" + PR_ID)).thenReturn(false, true);

        notifier.handle(event());
        notifier.handle(event());

        verify(slackService, times(1)).sendSlackNotification(anyString(), anyString());
        verify(emailService, times(1)).sendEmailNotification(anyString(), anyString(), anyString());
    }

    @Test
    void sendsNothingWhenAConcurrentDeliveryWonTheUniqueIndex() {
        activeRules(rule(5, PROJECT, "HIGH_RISK_PR", "#risk"));
        when(alertRepository.saveAndFlush(any(Alert.class))).thenThrow(new DataIntegrityViolationException("uq_alerts_dedup_key"));

        notifier.handle(event());

        verifyNoInteractions(slackService, emailService, notificationRepository);
    }

    @Test
    void recordsFailedDeliveriesAsFailed() {
        activeRules(rule(5, PROJECT, "HIGH_RISK_PR", "#risk"));
        when(slackService.sendSlackNotification(anyString(), anyString())).thenReturn(false);
        when(emailService.sendEmailNotification(anyString(), anyString(), anyString())).thenReturn(false);

        notifier.handle(event());

        ArgumentCaptor<Notification> notifications = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(notifications.capture());
        notifications.getAllValues().forEach(notification -> {
            assertEquals("failed", notification.getStatus());
            assertNull(notification.getSentAt());
        });
    }

    @Test
    void raisesNothingForAMergedOrClosedPullRequestThatASyncReScored() {
        activeRules(rule(5, PROJECT, "HIGH_RISK_PR", "#risk"));

        for (String state : new String[] {"merged", "closed"}) {
            pullRequestIs(state);
            notifier.handle(event());
        }

        verifyNoInteractions(alertRepository, slackService, emailService, notificationRepository);
    }

    @Test
    void ignoresAPullRequestWhoseRepositoryIsNotLinkedToAProject() {
        when(projectDirectory.findPullRequest(COMPANY, PR_ID)).thenReturn(Optional.empty());

        notifier.handle(event());

        verifyNoInteractions(alertRuleRepository, alertRepository, slackService, emailService);
    }
}
