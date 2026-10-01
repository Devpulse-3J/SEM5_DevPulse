package com.devpulse.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.devpulse.contracts.events.DeploymentCreatedEvent;
import com.devpulse.notification.email.EmailNotificationService;
import com.devpulse.notification.entity.Alert;
import com.devpulse.notification.entity.Notification;
import com.devpulse.notification.repository.AlertRepository;
import com.devpulse.notification.repository.NotificationRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository.DeployedProject;
import com.devpulse.notification.repository.ProjectDirectoryRepository.Manager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class DeploymentFailureNotifierTest {

    private static final int COMPANY = 15;
    private static final int GITHUB_REPO_ID = 1296161496;
    private static final String SHA = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678";

    private ProjectDirectoryRepository projectDirectory;
    private AlertRepository alertRepository;
    private NotificationRepository notificationRepository;
    private EmailNotificationService emailService;
    private DeploymentFailureNotifier notifier;

    @BeforeEach
    void setUp() {
        projectDirectory = mock(ProjectDirectoryRepository.class);
        alertRepository = mock(AlertRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        emailService = mock(EmailNotificationService.class);
        notifier = new DeploymentFailureNotifier(projectDirectory, alertRepository, notificationRepository, emailService);

        when(projectDirectory.findProjectForDeployment(COMPANY, GITHUB_REPO_ID, SHA)).thenReturn(Optional.of(
                new DeployedProject(20, "frontend", "Devpulse-3J/SEM5_DevPulse_Frontend")));
        when(projectDirectory.findManagers(20)).thenReturn(List.of(new Manager(31, "manager@example.com", "Mana Ger")));
        when(alertRepository.saveAndFlush(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setAlertId(77);
            return alert;
        });
        when(emailService.sendEmailNotification(anyString(), anyString(), anyString())).thenReturn(true);
    }

    private DeploymentCreatedEvent event(String status, int deploymentId) {
        return new DeploymentCreatedEvent("evt-" + deploymentId, COMPANY, GITHUB_REPO_ID,
                Instant.parse("2026-10-01T05:12:00Z"), deploymentId, SHA, "production", status,
                Instant.parse("2026-10-01T05:12:00Z"));
    }

    @Test
    void emailsTheProjectManagerWithTheDetailsOfTheFailedDeployment() {
        notifier.handle(event("failure", 900));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailNotification(eq("manager@example.com"), subject.capture(), body.capture());

        assertEquals("Deployment failed in frontend (a1b2c3d)", subject.getValue());
        String text = body.getValue();
        assertTrue(text.contains("Project:     frontend"));
        assertTrue(text.contains("Repository:  Devpulse-3J/SEM5_DevPulse_Frontend"));
        assertTrue(text.contains("Commit:      a1b2c3d"));
        assertTrue(text.contains("Time:        2026-10-01 05:12 UTC"));
        assertTrue(text.contains("https://github.com/Devpulse-3J/SEM5_DevPulse_Frontend/commit/" + SHA));
        assertTrue(text.contains("https://github.com/Devpulse-3J/SEM5_DevPulse_Frontend/actions"));

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notification.capture());
        assertEquals(31, notification.getValue().getUserId());
        assertEquals("email", notification.getValue().getChannel());
        assertEquals("sent", notification.getValue().getStatus());
    }

    @Test
    void recordsTheAlertAgainstTheRealProjectWithADedupKey() {
        notifier.handle(event("failure", 900));

        ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).saveAndFlush(alert.capture());
        assertEquals(20, alert.getValue().getProjectId());
        assertEquals("deployment", alert.getValue().getEntityType());
        assertEquals("deployment-failed:20:" + SHA, alert.getValue().getDedupKey());
    }

    @Test
    void treatsGithubsErrorStateAsAFailureToo() {
        notifier.handle(event("error", 900));
        verify(emailService).sendEmailNotification(anyString(), anyString(), anyString());
    }

    @Test
    void ignoresDeploymentsThatDidNotFail() {
        for (String status : new String[] {"success", "pending", "in_progress", "rolled_back"}) {
            notifier.handle(event(status, 900));
        }
        verifyNoInteractions(alertRepository, emailService, notificationRepository);
    }

    @Test
    void sendsOnlyOneEmailWhenTheSameFailureArrivesAsASecondEvent() {
        // workflow_job and deployment_status report the same failed commit with different ids.
        when(alertRepository.existsByDedupKey("deployment-failed:20:" + SHA)).thenReturn(false, true);

        notifier.handle(event("failure", 900));
        notifier.handle(event("failure", 901));

        verify(emailService, times(1)).sendEmailNotification(anyString(), anyString(), anyString());
        verify(alertRepository, times(1)).saveAndFlush(any(Alert.class));
    }

    @Test
    void sendsNothingWhenAConcurrentDeliveryWonTheUniqueIndex() {
        when(alertRepository.saveAndFlush(any(Alert.class))).thenThrow(new DataIntegrityViolationException("uq_alerts_dedup_key"));

        notifier.handle(event("failure", 900));

        verifyNoInteractions(emailService, notificationRepository);
    }

    @Test
    void recordsTheAlertButEmailsNobodyWhenTheProjectHasNoManager() {
        when(projectDirectory.findManagers(20)).thenReturn(List.of());

        notifier.handle(event("failure", 900));

        verify(alertRepository).saveAndFlush(any(Alert.class));
        verifyNoInteractions(emailService, notificationRepository);
    }

    @Test
    void doesNothingWhenTheDeploymentMatchesNoLinkedProject() {
        when(projectDirectory.findProjectForDeployment(COMPANY, GITHUB_REPO_ID, SHA)).thenReturn(Optional.empty());

        notifier.handle(event("failure", 900));

        verifyNoInteractions(alertRepository, emailService, notificationRepository);
    }

    @Test
    void recordsAFailedNotificationWhenTheEmailCouldNotBeSent() {
        when(emailService.sendEmailNotification(anyString(), anyString(), anyString())).thenReturn(false);

        notifier.handle(event("failure", 900));

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notification.capture());
        assertEquals("failed", notification.getValue().getStatus());
        assertNull(notification.getValue().getSentAt());
    }
}
