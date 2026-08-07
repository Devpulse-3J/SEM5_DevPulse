package com.devpulse.integration.entity;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class EntityTest {

    @Test
    public void testRawEventLogEntity() {
        RawEventLog log = new RawEventLog(1, "github", "push", "{\"ref\":\"refs/heads/main\"}");
        log.setEventId(10);
        
        assertEquals(10, log.getEventId());
        assertEquals(1, log.getCompanyId());
        assertEquals("github", log.getProvider());
        assertEquals("push", log.getEventType());
        assertEquals("{\"ref\":\"refs/heads/main\"}", log.getPayload());
        assertNotNull(log.getReceivedAt());
        assertNull(log.getProcessedAt());

        Instant now = Instant.now();
        log.setProcessedAt(now);
        assertEquals(now, log.getProcessedAt());
    }

    @Test
    public void testRepoEntity() {
        Repo repo = new Repo(1, 10, 123456L, "devpulse", "devpulse-org", "devpulse-org/devpulse", "main");
        repo.setRepoId(5);

        assertEquals(5, repo.getRepoId());
        assertEquals(1, repo.getCompanyId());
        assertEquals(10, repo.getProjectId());
        assertEquals(123456L, repo.getGithubRepoId());
        assertEquals("devpulse", repo.getRepoName());
        assertEquals("devpulse-org", repo.getOwnerName());
        assertEquals("devpulse-org/devpulse", repo.getFullName());
        assertEquals("main", repo.getDefaultBranch());
    }

    @Test
    public void testJiraIssueEntity() {
        JiraIssue issue = new JiraIssue(1, 10, "DP-1", "Fix login bug", "Bug", "High", "In Progress", 3, 2);
        issue.setIssueId(20);

        assertEquals(20, issue.getIssueId());
        assertEquals(1, issue.getCompanyId());
        assertEquals(10, issue.getProjectId());
        assertEquals("DP-1", issue.getJiraKey());
        assertEquals("Fix login bug", issue.getSummary());
        assertEquals("Bug", issue.getIssueType());
        assertEquals("High", issue.getPriority());
        assertEquals("In Progress", issue.getStatus());
        assertEquals(3, issue.getStoryPoints());
        assertEquals(2, issue.getAssigneeId());
        assertNotNull(issue.getCreatedAt());
        assertNull(issue.getClosedAt());
    }
}
