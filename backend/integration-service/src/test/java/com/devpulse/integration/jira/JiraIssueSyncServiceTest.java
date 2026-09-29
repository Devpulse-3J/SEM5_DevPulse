package com.devpulse.integration.jira;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.devpulse.integration.entity.JiraConnection;
import com.devpulse.integration.entity.JiraIssue;
import com.devpulse.integration.repository.JiraConnectionRepository;
import com.devpulse.integration.repository.JiraIssueRepository;
import com.devpulse.integration.repository.TenantAccessRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

public class JiraIssueSyncServiceTest {

    private JiraCloudClient client;
    private JiraConnectionRepository connectionRepository;
    private JiraIssueRepository issueRepository;
    private TenantAccessRepository tenantAccessRepository;
    private JiraIssueSyncService service;
    private JiraConnection connection;

    @BeforeEach
    public void setUp() {
        client = mock(JiraCloudClient.class);
        connectionRepository = mock(JiraConnectionRepository.class);
        issueRepository = mock(JiraIssueRepository.class);
        tenantAccessRepository = mock(TenantAccessRepository.class);
        service = new JiraIssueSyncService(client, connectionRepository, issueRepository, tenantAccessRepository);

        connection = new JiraConnection();
        connection.setCompanyId(1);
        connection.setCloudId("cloud-123");
        connection.setAccessToken("token-abc");
        connection.setRefreshToken("refresh-abc");

        when(issueRepository.findByCompanyIdAndJiraKey(anyInt(), anyString())).thenReturn(Optional.empty());
        when(tenantAccessRepository.findProjectIdByJiraKey(anyInt(), any())).thenReturn(Optional.empty());
        when(tenantAccessRepository.findUserIdByEmail(anyInt(), any())).thenReturn(Optional.empty());
    }

    @Test
    public void upsertsIssuesFromOnePage() {
        var issue = new JiraCloudClient.JiraIssueData(
                "DEV-1", "Fix bug", "Bug", "High", "In Progress", 5, "DEV", "dev@company.com", false);
        when(client.searchIssues(eq("cloud-123"), eq("token-abc"), eq(0), eq(100)))
                .thenReturn(List.of(issue));
        when(client.searchIssues(eq("cloud-123"), eq("token-abc"), eq(100), eq(100)))
                .thenReturn(List.of());

        int synced = service.sync(connection);

        assertEquals(1, synced);
        verify(issueRepository, times(1)).save(any(JiraIssue.class));
    }

    @Test
    public void refreshesTokenOnceOn401ThenRetries() {
        when(client.searchIssues(eq("cloud-123"), eq("token-abc"), eq(0), eq(100)))
                .thenThrow(new JiraCloudClient.JiraApiException("unauthorized",
                        HttpClientErrorException.create(
                                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null)));
        when(client.refreshAccessToken("refresh-abc"))
                .thenReturn(new JiraCloudClient.TokenResult("token-new", "refresh-new"));
        when(client.searchIssues(eq("cloud-123"), eq("token-new"), eq(0), eq(100)))
                .thenReturn(List.of());

        int synced = service.sync(connection);

        assertEquals(0, synced);
        assertEquals("token-new", connection.getAccessToken());
        assertEquals("refresh-new", connection.getRefreshToken());
        verify(connectionRepository, times(1)).save(connection);
    }

    @Test
    public void stopsPagingWhenBatchSmallerThanPageSize() {
        var issue = new JiraCloudClient.JiraIssueData(
                "DEV-1", "Small batch", "Task", "Low", "To Do", null, "DEV", null, false);
        when(client.searchIssues(eq("cloud-123"), eq("token-abc"), eq(0), eq(100)))
                .thenReturn(List.of(issue));

        service.sync(connection);

        // Only the first page should ever be requested once the returned batch is
        // smaller than the page size — no startAt=100 call.
        verify(client, times(1)).searchIssues(anyString(), anyString(), anyInt(), anyInt());
    }
}
