package com.devpulse.notification.service;

import com.devpulse.contracts.events.AlertPrHighRiskEvent;
import com.devpulse.contracts.events.PrOpenedEvent;
import com.devpulse.notification.email.EmailNotificationService;
import com.devpulse.notification.entity.Alert;
import com.devpulse.notification.entity.Notification;
import com.devpulse.notification.repository.AlertRepository;
import com.devpulse.notification.repository.NotificationRepository;
import com.devpulse.notification.slack.SlackNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SlackNotificationService slackNotificationService;

    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void testHandleHighRiskPrAlertEvent() {
        Alert savedAlert = new Alert(1, 10, null, "pull_request", 101, "critical", "Test High Risk");
        savedAlert.setAlertId(55);

        when(alertRepository.save(any(Alert.class))).thenReturn(savedAlert);
        when(slackNotificationService.sendSlackNotification(anyString(), anyString())).thenReturn(true);

        AlertPrHighRiskEvent highRiskEvent = new AlertPrHighRiskEvent(
                UUID.randomUUID().toString(),
                1, 10, Instant.now(),
                100, 101, "random_forest", "v1.0", "high", 0.88, 0.95, Instant.now()
        );

        listener.handleIncomingEvent(highRiskEvent);

        verify(alertRepository, times(1)).save(any(Alert.class));
        verify(slackNotificationService, times(1)).sendSlackNotification(eq("#dev-alerts"), anyString());
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void testHandlePrOpenedEvent() {
        PrOpenedEvent prOpened = new PrOpenedEvent(
                UUID.randomUUID().toString(), 1, 10, Instant.now(),
                101, 5, 42, "Refactor core", 20, "main", false, 10, 2, 1
        );

        listener.handleIncomingEvent(prOpened);

        verify(alertRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }
}
