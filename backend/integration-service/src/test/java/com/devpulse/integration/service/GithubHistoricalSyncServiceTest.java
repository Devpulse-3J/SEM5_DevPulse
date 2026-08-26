package com.devpulse.integration.service;

import com.devpulse.contracts.events.BaseEvent;
import com.devpulse.integration.client.GithubApiClient;
import com.devpulse.integration.entity.RawEventLog;
import com.devpulse.integration.entity.Repo;
import com.devpulse.integration.repository.RawEventLogRepository;
import com.devpulse.integration.repository.RepoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GithubHistoricalSyncServiceTest {

    private GithubApiClient githubApiClient;
    private RepoRepository repoRepository;
    private RawEventLogRepository rawEventLogRepository;
    private EventPublisherService eventPublisherService;
    private ObjectMapper objectMapper;
    private GithubHistoricalSyncService syncService;

    @BeforeEach
    public void setUp() {
        githubApiClient = mock(GithubApiClient.class);
        repoRepository = mock(RepoRepository.class);
        rawEventLogRepository = mock(RawEventLogRepository.class);
        eventPublisherService = mock(EventPublisherService.class);
        objectMapper = new ObjectMapper();

        syncService = new GithubHistoricalSyncService(
                githubApiClient,
                repoRepository,
                rawEventLogRepository,
                eventPublisherService,
                objectMapper
        );
    }

    @Test
    public void testSyncHistoricalProjectDataSuccessful() {
        ObjectNode repoJson = objectMapper.createObjectNode();
        repoJson.put("id", 12345L);
        repoJson.put("name", "Hello-World");
        repoJson.putObject("owner").put("login", "octocat");
        repoJson.put("full_name", "octocat/Hello-World");
        repoJson.put("default_branch", "main");

        ArrayNode prsJson = objectMapper.createArrayNode();
        ObjectNode pr1 = objectMapper.createObjectNode();
        pr1.put("id", 101);
        pr1.put("number", 1);
        pr1.put("title", "Fix issue");
        pr1.put("state", "closed");
        pr1.put("merged_at", "2026-08-20T10:00:00Z");
        pr1.putObject("user").put("id", 42);
        pr1.putObject("base").put("ref", "main");
        prsJson.add(pr1);

        ArrayNode commitsJson = objectMapper.createArrayNode();
        ObjectNode commit1 = objectMapper.createObjectNode();
        commit1.put("sha", "abc1234");
        commit1.putObject("commit").put("message", "First commit");
        commit1.putObject("author").put("id", 42);
        commitsJson.add(commit1);

        when(githubApiClient.fetchRepositoryDetails("octocat", "Hello-World")).thenReturn(repoJson);
        when(githubApiClient.fetchPullRequests("octocat", "Hello-World", "all")).thenReturn(prsJson);
        when(githubApiClient.fetchCommits("octocat", "Hello-World")).thenReturn(commitsJson);

        Repo dummyRepo = new Repo(1, 1, 12345L, "Hello-World", "octocat", "octocat/Hello-World", "main");
        dummyRepo.setRepoId(1);
        when(repoRepository.findByCompanyIdAndGithubRepoId(eq(1), eq(12345L))).thenReturn(Optional.of(dummyRepo));
        when(repoRepository.save(any(Repo.class))).thenReturn(dummyRepo);

        Map<String, Object> result = syncService.syncHistoricalProjectData("octocat", "Hello-World", 1, 1);

        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals(1, result.get("prsSynced"));
        assertEquals(1, result.get("commitsSynced"));

        verify(rawEventLogRepository, atLeastOnce()).save(any(RawEventLog.class));
        verify(eventPublisherService, atLeastOnce()).publishEvent(any(BaseEvent.class));
    }
}
