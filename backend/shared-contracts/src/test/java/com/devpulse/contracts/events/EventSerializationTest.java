package com.devpulse.contracts.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class EventSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    public void testPrOpenedEventSerialization() throws Exception {
        Instant now = Instant.now();
        PrOpenedEvent event = new PrOpenedEvent(
                "evt-101", 1, 10, now,
                100, 5, 42, "feat: add user auth",
                3, "main", false, 150, 20, 4
        );

        String json = objectMapper.writeValueAsString(event);
        assertNotNull(json);
        assertTrue(json.contains("pr.opened"));
        assertTrue(json.contains("feat: add user auth"));

        PrOpenedEvent deserialized = objectMapper.readValue(json, PrOpenedEvent.class);
        assertEquals("evt-101", deserialized.getEventId());
        assertEquals(1, deserialized.getCompanyId());
        assertEquals(10, deserialized.getProjectId());
        assertEquals("pr.opened", deserialized.getEventType());
        assertEquals(100, deserialized.getPrId());
        assertEquals(5, deserialized.getRepoId());
        assertEquals(42, deserialized.getGithubPrNumber());
        assertEquals("feat: add user auth", deserialized.getTitle());
        assertEquals(3, deserialized.getAuthorId());
        assertEquals("main", deserialized.getBaseBranch());
        assertFalse(deserialized.isDraft());
        assertEquals(150, deserialized.getLinesAdded());
        assertEquals(20, deserialized.getLinesDeleted());
        assertEquals(4, deserialized.getFilesChanged());
    }

    @Test
    public void testPrMergedEventSerialization() throws Exception {
        Instant now = Instant.now();
        PrMergedEvent event = new PrMergedEvent(
                "evt-102", 1, 10, now, 100, 5, now
        );

        String json = objectMapper.writeValueAsString(event);
        assertTrue(json.contains("pr.merged"));

        PrMergedEvent deserialized = objectMapper.readValue(json, PrMergedEvent.class);
        assertEquals("evt-102", deserialized.getEventId());
        assertEquals("pr.merged", deserialized.getEventType());
        assertEquals(100, deserialized.getPrId());
    }

    @Test
    public void testPrClosedEventSerialization() throws Exception {
        Instant now = Instant.now();
        PrClosedEvent event = new PrClosedEvent(
                "evt-103", 1, 10, now, 100, 5, now
        );

        String json = objectMapper.writeValueAsString(event);
        assertTrue(json.contains("pr.closed"));

        PrClosedEvent deserialized = objectMapper.readValue(json, PrClosedEvent.class);
        assertEquals("evt-103", deserialized.getEventId());
        assertEquals("pr.closed", deserialized.getEventType());
        assertEquals(100, deserialized.getPrId());
    }

    @Test
    public void testCommitPushedEventSerialization() throws Exception {
        Instant now = Instant.now();
        CommitPushedEvent event = new CommitPushedEvent(
                "evt-104", 1, 10, now,
                "a1b2c3d4e5f67890123456789012345678901234", 5, 100, 3,
                "fix: typo in readme", now, 10, 2
        );

        String json = objectMapper.writeValueAsString(event);
        assertTrue(json.contains("commit.pushed"));

        CommitPushedEvent deserialized = objectMapper.readValue(json, CommitPushedEvent.class);
        assertEquals("evt-104", deserialized.getEventId());
        assertEquals("a1b2c3d4e5f67890123456789012345678901234", deserialized.getCommitSha());
        assertEquals("fix: typo in readme", deserialized.getMessage());
    }

    @Test
    public void testDeploymentCreatedEventSerialization() throws Exception {
        Instant now = Instant.now();
        DeploymentCreatedEvent event = new DeploymentCreatedEvent(
                "evt-105", 1, 10, now,
                50, "a1b2c3d4e5f67890123456789012345678901234", "production",
                "success", now
        );

        String json = objectMapper.writeValueAsString(event);
        assertTrue(json.contains("deployment.created"));

        DeploymentCreatedEvent deserialized = objectMapper.readValue(json, DeploymentCreatedEvent.class);
        assertEquals("evt-105", deserialized.getEventId());
        assertEquals("production", deserialized.getEnvironment());
        assertEquals("success", deserialized.getStatus());
    }

    @Test
    public void testIssueUpdatedEventSerialization() throws Exception {
        Instant now = Instant.now();
        IssueUpdatedEvent event = new IssueUpdatedEvent(
                "evt-106", 1, 10, now,
                200, "DP-101", "Implement auth flow", "Story",
                "High", "In Progress", 5, 3, now
        );

        String json = objectMapper.writeValueAsString(event);
        assertTrue(json.contains("issue.updated"));

        IssueUpdatedEvent deserialized = objectMapper.readValue(json, IssueUpdatedEvent.class);
        assertEquals("evt-106", deserialized.getEventId());
        assertEquals("DP-101", deserialized.getJiraKey());
        assertEquals("In Progress", deserialized.getStatus());
        assertEquals(5, deserialized.getStoryPoints());
    }

    @Test
    public void testAlertPrHighRiskEventSerialization() throws Exception {
        Instant now = Instant.now();
        AlertPrHighRiskEvent event = new AlertPrHighRiskEvent(
                "evt-107", 1, 10, now,
                300, 100, "xgboost", "v1.0.0",
                "high", 0.875, 0.95, now
        );

        String json = objectMapper.writeValueAsString(event);
        assertTrue(json.contains("alert.pr_high_risk"));

        AlertPrHighRiskEvent deserialized = objectMapper.readValue(json, AlertPrHighRiskEvent.class);
        assertEquals("evt-107", deserialized.getEventId());
        assertEquals("xgboost", deserialized.getAlgorithm());
        assertEquals("high", deserialized.getRiskCategory());
        assertEquals(0.875, deserialized.getRiskScore(), 0.0001);
    }
}
