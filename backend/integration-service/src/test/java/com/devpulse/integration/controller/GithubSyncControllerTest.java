package com.devpulse.integration.controller;

import com.devpulse.integration.service.GithubHistoricalSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GithubSyncControllerTest {

    private GithubHistoricalSyncService syncService;
    private GithubSyncController controller;

    @BeforeEach
    public void setUp() {
        syncService = mock(GithubHistoricalSyncService.class);
        controller = new GithubSyncController(syncService);
    }

    @Test
    public void testSyncHistoricalDataEndpoint() {
        when(syncService.syncHistoricalProjectData("octocat", "Hello-World", 1, 1))
                .thenReturn(Map.of("status", "success", "prsSynced", 5, "commitsSynced", 10));

        ResponseEntity<Map<String, Object>> response = controller.syncHistoricalData("octocat", "Hello-World", 1, 1);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("success", response.getBody().get("status"));
        assertEquals(5, response.getBody().get("prsSynced"));
        verify(syncService).syncHistoricalProjectData("octocat", "Hello-World", 1, 1);
    }
}
