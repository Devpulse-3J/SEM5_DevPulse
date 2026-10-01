package com.devpulse.notification.service;

import com.devpulse.contracts.events.AlertPrHighRiskEvent;
import com.devpulse.contracts.events.DeploymentCreatedEvent;
import com.devpulse.contracts.events.PrOpenedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private HighRiskPrNotifier highRiskPrNotifier;

    @Mock
    private DeploymentFailureNotifier deploymentFailureNotifier;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void routesDeploymentEventsToTheFailureNotifier() {
        DeploymentCreatedEvent event = new DeploymentCreatedEvent(
                UUID.randomUUID().toString(), 15, 1296161496, Instant.now(),
                900, "a1b2c3d", "production", "failure", Instant.now());

        listener.handleIncomingEvent(event);

        verify(deploymentFailureNotifier).handle(event);
        verifyNoInteractions(highRiskPrNotifier);
    }

    @Test
    void routesHighRiskAlertsToTheHighRiskNotifier() {
        AlertPrHighRiskEvent event = new AlertPrHighRiskEvent(
                UUID.randomUUID().toString(), 1, 10, Instant.now(),
                100, 101, "random_forest", "v1.0", "high", 0.88, 0.95, Instant.now());

        listener.handleIncomingEvent(event);

        verify(highRiskPrNotifier).handle(event);
        verifyNoInteractions(deploymentFailureNotifier);
    }

    @Test
    void onlyLogsPrOpenedEvents() {
        PrOpenedEvent prOpened = new PrOpenedEvent(
                UUID.randomUUID().toString(), 1, 10, Instant.now(),
                101, 5, 42, "Refactor core", 20, "main", false, 10, 2, 1
        );

        listener.handleIncomingEvent(prOpened);

        verifyNoInteractions(highRiskPrNotifier, deploymentFailureNotifier);
    }
}
