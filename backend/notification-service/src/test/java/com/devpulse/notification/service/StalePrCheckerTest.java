package com.devpulse.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StalePrCheckerTest {

    private static final int COMPANY = 15;
    private static final int BACKEND = 8;
    private static final int FRONTEND = 20;
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private AlertRuleRepository alertRuleRepository;
    private ProjectDirectoryRepository projectDirectory;
    private AlertRepository alertRepository;
    private NotificationRepository notificationRepository;
    private SlackNotificationService slackService;
    private EmailNotificationService emailService;
    private StalePrChecker checker;

    @BeforeEach
    void setUp() {
        alertRuleRepository = mock(AlertRuleRepository.class);
        projectDirectory = mock(ProjectDirectoryRepository.class);
        alertRepository = mock(AlertRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        slackService = mock(SlackNotificationService.class);
        emailService = mock(EmailNotificationService.class);
        checker = new StalePrChecker(alertRuleRepository, projectDirectory, new PrAlertDispatcher(
                projectDirectory, alertRepository, notificationRepository, slackService, emailService));

        AtomicInteger ids = new AtomicInteger(100);
        when(alertRepository.saveAndFlush(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setAlertId(ids.incrementAndGet());
            return alert;
        });
        when(projectDirectory.findManagers(BACKEND)).thenReturn(List.of(new Manager(40, "backend-manager@example.com", "B")));
        when(projectDirectory.findManagers(FRONTEND)).thenReturn(List.of(new Manager(22, "frontend-manager@example.com", "F")));
        when(slackService.sendSlackNotification(anyString(), anyString())).thenReturn(true);
        when(emailService.sendEmailNotification(anyString(), anyString(), anyString())).thenReturn(true);
    }

    private static AlertRule rule(int id, Integer projectId, String type, Integer hours, String channel) {
        AlertRule rule = new AlertRule(COMPANY, projectId, type, hours, channel, 40);
        rule.setRuleId(id);
        return rule;
    }

    private static ProjectPullRequest pr(int prId, int number, int projectId, long hoursOpen) {
        return new ProjectPullRequest(prId, number, "PR " + number, null, "open", NOW.minus(Duration.ofHours(hoursOpen)),
                projectId, projectId == BACKEND ? "Dev_pulse_Backend" : "frontend",
                projectId == BACKEND ? "Devpulse-3J/SEM5_DevPulse" : "Devpulse-3J/SEM5_DevPulse_Frontend");
    }

    private void activeRules(AlertRule... rules) {
        when(alertRuleRepository.findByIsActiveTrue()).thenReturn(List.of(rules));
    }

    private void openPullRequests(ProjectPullRequest... prs) {
        when(projectDirectory.findOpenPullRequestsOpenedBefore(eq(COMPANY), any(Instant.class))).thenReturn(List.of(prs));
    }

    @Test
    void reportsAPullRequestOpenLongerThanTheRulesThreshold() {
        activeRules(rule(2, null, "STALE_PR", 24, "#eng-alert"));
        openPullRequests(pr(412, 63, BACKEND, 30));

        checker.check(NOW);

        // Only pull requests opened before now minus the threshold are fetched at all.
        verify(projectDirectory).findOpenPullRequestsOpenedBefore(COMPANY, NOW.minus(Duration.ofHours(24)));

        ArgumentCaptor<String> slackText = ArgumentCaptor.forClass(String.class);
        verify(slackService).sendSlackNotification(eq("#eng-alert"), slackText.capture());
        assertTrue(slackText.getValue().startsWith("1 stale pull request in Dev_pulse_Backend"));
        assertTrue(slackText.getValue().contains("#63 PR 63 - open 30 hours"));
        assertTrue(slackText.getValue().contains("https://github.com/Devpulse-3J/SEM5_DevPulse/pull/63"));

        verify(emailService).sendEmailNotification(eq("backend-manager@example.com"),
                eq("1 stale pull request in Dev_pulse_Backend"), contains("#63 PR 63 - open 30 hours"));

        ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).saveAndFlush(alert.capture());
        assertEquals("stale-pr:412", alert.getValue().getDedupKey());
        assertEquals(2, alert.getValue().getRuleId());
        assertEquals(BACKEND, alert.getValue().getProjectId());
        assertEquals("warning", alert.getValue().getSeverity());
    }

    @Test
    void usesTheShortestThresholdToFetchAndEachRulesOwnThresholdToMatch() {
        activeRules(rule(2, BACKEND, "STALE_PR", 72, "#slow"), rule(3, null, "STALE_PR", 24, "#fast"));
        openPullRequests(pr(412, 63, BACKEND, 30));

        checker.check(NOW);

        verify(projectDirectory).findOpenPullRequestsOpenedBefore(COMPANY, NOW.minus(Duration.ofHours(24)));
        verify(slackService).sendSlackNotification(eq("#fast"), anyString());
        verify(slackService, never()).sendSlackNotification(eq("#slow"), anyString());
    }

    @Test
    void aProjectRuleDoesNotCoverAnotherProjectsPullRequests() {
        activeRules(rule(4, FRONTEND, "STALE_PR", 24, "#frontend"));
        openPullRequests(pr(412, 63, BACKEND, 30));

        checker.check(NOW);

        verifyNoInteractions(alertRepository, slackService, emailService);
    }

    @Test
    void twoRulesForTheSamePullRequestGiveOneAlertOneEmailAndBothChannels() {
        activeRules(rule(2, null, "STALE_PR", 24, "#eng-alert"), rule(3, null, "STALE_PR", 24, "#new-channel"));
        openPullRequests(pr(412, 63, BACKEND, 30));

        checker.check(NOW);

        verify(alertRepository, times(1)).saveAndFlush(any(Alert.class));
        verify(slackService).sendSlackNotification(eq("#eng-alert"), anyString());
        verify(slackService).sendSlackNotification(eq("#new-channel"), anyString());
        verify(emailService, times(1)).sendEmailNotification(anyString(), anyString(), anyString());
    }

    @Test
    void groupsNewlyStalePullRequestsIntoOneMessagePerProject() {
        activeRules(rule(2, null, "STALE_PR", 24, "#eng-alert"));
        openPullRequests(pr(412, 63, BACKEND, 30), pr(413, 64, BACKEND, 100), pr(500, 12, FRONTEND, 26));

        checker.check(NOW);

        verify(slackService, times(2)).sendSlackNotification(eq("#eng-alert"), anyString());
        verify(emailService).sendEmailNotification(eq("backend-manager@example.com"),
                eq("2 stale pull requests in Dev_pulse_Backend"), contains("#64 PR 64 - open 4 days"));
        verify(emailService).sendEmailNotification(eq("frontend-manager@example.com"),
                eq("1 stale pull request in frontend"), anyString());
        verify(alertRepository, times(3)).saveAndFlush(any(Alert.class));
        // 3 alerts, each logged once for Slack and once for its project's manager.
        verify(notificationRepository, times(6)).save(any(Notification.class));
    }

    @Test
    void doesNotReportTheSamePullRequestOnTheNextCheck() {
        activeRules(rule(2, null, "STALE_PR", 24, "#eng-alert"));
        openPullRequests(pr(412, 63, BACKEND, 30));
        when(alertRepository.existsByDedupKey("stale-pr:412")).thenReturn(false, true);

        checker.check(NOW);
        checker.check(NOW.plus(Duration.ofMinutes(15)));

        verify(slackService, times(1)).sendSlackNotification(anyString(), anyString());
        verify(emailService, times(1)).sendEmailNotification(anyString(), anyString(), anyString());
    }

    @Test
    void ignoresInactiveTypesAndFallsBackTo24HoursWhenARuleHasNoThreshold() {
        activeRules(rule(6, null, "HIGH_RISK_PR", 1, "#risk"), rule(7, null, "stale_pull_request", null, "#dev-alerts"));
        openPullRequests(pr(412, 63, BACKEND, 30));

        checker.check(NOW);

        verify(projectDirectory).findOpenPullRequestsOpenedBefore(COMPANY, NOW.minus(Duration.ofHours(24)));
        verify(slackService).sendSlackNotification(eq("#dev-alerts"), anyString());
        verify(slackService, never()).sendSlackNotification(eq("#risk"), anyString());
    }

    @Test
    void stillEmailsTheManagerWhenTheRuleHasNoSlackChannel() {
        activeRules(rule(2, null, "STALE_PR", 24, " "));
        openPullRequests(pr(412, 63, BACKEND, 30));

        checker.check(NOW);

        verifyNoInteractions(slackService);
        verify(emailService).sendEmailNotification(eq("backend-manager@example.com"), anyString(), anyString());
    }

    @Test
    void aFailureInOneCompanyDoesNotStopTheOthers() {
        AlertRule other = new AlertRule(12, null, "STALE_PR", 24, "#other", 1);
        other.setRuleId(1);
        activeRules(other, rule(2, null, "STALE_PR", 24, "#eng-alert"));
        when(projectDirectory.findOpenPullRequestsOpenedBefore(eq(12), any(Instant.class)))
                .thenThrow(new IllegalStateException("database unavailable"));
        openPullRequests(pr(412, 63, BACKEND, 30));

        assertDoesNotThrow(() -> checker.check(NOW));

        verify(slackService).sendSlackNotification(eq("#eng-alert"), anyString());
    }

    @Test
    void doesNothingWhenThereAreNoStaleRules() {
        activeRules();

        checker.check(NOW);

        verifyNoInteractions(projectDirectory, alertRepository, slackService, emailService);
    }
}
