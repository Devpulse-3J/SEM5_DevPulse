package com.devpulse.integration.service;

import com.devpulse.contracts.events.BaseEvent;
import com.devpulse.contracts.events.CommitPushedEvent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
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

    // -- commit events must carry the commit's own time and an honest author ---
    //
    // Commits imported by this sync used to be stamped with the moment the sync
    // ran, which is later than the deployments that shipped them, so lead time came
    // out negative and was discarded. A commit with no author id was also given
    // author 1.

    private List<CommitPushedEvent> syncCommits(ArrayNode commits) {
        ObjectNode repoJson = objectMapper.createObjectNode();
        repoJson.put("id", 12345L);
        repoJson.put("name", "Hello-World");
        repoJson.putObject("owner").put("login", "octocat");
        repoJson.put("full_name", "octocat/Hello-World");
        repoJson.put("default_branch", "main");

        when(githubApiClient.fetchRepositoryDetails("octocat", "Hello-World")).thenReturn(repoJson);
        when(githubApiClient.fetchPullRequests("octocat", "Hello-World", "all")).thenReturn(objectMapper.createArrayNode());
        when(githubApiClient.fetchCommits("octocat", "Hello-World")).thenReturn(commits);
        Repo repo = new Repo(1, 1, 12345L, "Hello-World", "octocat", "octocat/Hello-World", "main");
        repo.setRepoId(1);
        when(repoRepository.findByCompanyIdAndGithubRepoId(eq(1), eq(12345L))).thenReturn(Optional.of(repo));
        when(repoRepository.save(any(Repo.class))).thenReturn(repo);

        syncService.syncHistoricalProjectData("octocat", "Hello-World", 1, 1);

        ArgumentCaptor<BaseEvent> published = ArgumentCaptor.forClass(BaseEvent.class);
        verify(eventPublisherService, atLeastOnce()).publishEvent(published.capture());
        return published.getAllValues().stream()
                .filter(CommitPushedEvent.class::isInstance).map(CommitPushedEvent.class::cast).toList();
    }

    @Test
    public void aSyncedCommitKeepsItsOwnCommitDateNotTheTimeOfTheSync() {
        ArrayNode commits = objectMapper.createArrayNode();
        ObjectNode commit = commits.addObject();
        commit.put("sha", "abc1234");
        ObjectNode details = commit.putObject("commit");
        details.put("message", "Real commit");
        details.putObject("author").put("date", "2026-08-01T08:00:00Z");
        details.putObject("committer").put("date", "2026-08-01T09:30:00Z");
        commit.putObject("author").put("id", 42);

        CommitPushedEvent event = syncCommits(commits).get(0);

        assertEquals(Instant.parse("2026-08-01T09:30:00Z"), event.getCommitTime());
        assertEquals(42, event.getAuthorId());
    }

    @Test
    public void aSyncedCommitFallsBackToTheAuthorDateThenToNow() {
        ArrayNode commits = objectMapper.createArrayNode();
        ObjectNode onlyAuthorDate = commits.addObject();
        onlyAuthorDate.put("sha", "aaa");
        onlyAuthorDate.putObject("commit").putObject("author").put("date", "2026-08-02T10:00:00Z");
        ObjectNode noDates = commits.addObject();
        noDates.put("sha", "bbb");
        noDates.putObject("commit").put("message", "no dates");

        Instant before = Instant.now();
        List<CommitPushedEvent> events = syncCommits(commits);

        assertEquals(Instant.parse("2026-08-02T10:00:00Z"), events.get(0).getCommitTime());
        assertFalse(events.get(1).getCommitTime().isBefore(before), "no date at all: falls back to now");
    }

    @Test
    public void aSyncedCommitWithNoAuthorHasNoAuthorInsteadOfUserOne() {
        ArrayNode commits = objectMapper.createArrayNode();
        ObjectNode commit = commits.addObject();
        commit.put("sha", "abc1234");
        commit.putObject("commit").put("message", "ghost commit");

        assertNull(syncCommits(commits).get(0).getAuthorId());
    }
}
