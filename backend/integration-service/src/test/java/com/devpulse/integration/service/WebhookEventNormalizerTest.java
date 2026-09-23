package com.devpulse.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devpulse.contracts.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebhookEventNormalizerTest {

    private WebhookEventNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new WebhookEventNormalizer(new ObjectMapper());
    }

    @Test
    void testNormalizePrOpenedEvent() {
        String json = "{\"action\":\"opened\",\"pull_request\":{\"id\":100,\"number\":12,\"title\":\"Feature PR\",\"user\":{\"id\":5},\"base\":{\"ref\":\"main\"},\"draft\":false,\"additions\":50,\"deletions\":10,\"changed_files\":3},\"repository\":{\"id\":77}}";

        BaseEvent event = normalizer.normalize("github", "pull_request", 1, json);

        assertNotNull(event);
        assertInstanceOf(PrOpenedEvent.class, event);
        PrOpenedEvent prOpened = (PrOpenedEvent) event;
        assertEquals("pr.opened", prOpened.getEventType());
        assertEquals("Feature PR", prOpened.getTitle());
        assertEquals(12, prOpened.getGithubPrNumber());
        assertEquals(77, prOpened.getRepoId());
    }

    @Test
    void testNormalizePrOpenedCarriesBodyAndAuthorAssociation() {
        // Both feed ML features downstream: body -> pull_requests.description,
        // author_association -> pull_requests.author_association.
        String json = "{\"action\":\"opened\",\"pull_request\":{\"id\":100,\"number\":12,"
                + "\"title\":\"Feature PR\",\"body\":\"Adds the thing.\","
                + "\"author_association\":\"CONTRIBUTOR\",\"user\":{\"id\":5},"
                + "\"base\":{\"ref\":\"main\"},\"draft\":false,\"additions\":50,"
                + "\"deletions\":10,\"changed_files\":3},\"repository\":{\"id\":77}}";

        PrOpenedEvent prOpened = (PrOpenedEvent) normalizer.normalize("github", "pull_request", 1, json);

        assertNotNull(prOpened);
        assertEquals("Adds the thing.", prOpened.getBody());
        assertEquals("CONTRIBUTOR", prOpened.getAuthorAssociation());
    }

    @Test
    void testNormalizePrOpenedWithoutBodyLeavesItNull() {
        // GitHub omits "body" entirely for a description-less PR. It must stay
        // null rather than becoming the string "null" or an empty string.
        String json = "{\"action\":\"opened\",\"pull_request\":{\"id\":100,\"number\":12,"
                + "\"title\":\"Feature PR\",\"user\":{\"id\":5},\"base\":{\"ref\":\"main\"},"
                + "\"draft\":false,\"additions\":50,\"deletions\":10,\"changed_files\":3},"
                + "\"repository\":{\"id\":77}}";

        PrOpenedEvent prOpened = (PrOpenedEvent) normalizer.normalize("github", "pull_request", 1, json);

        assertNotNull(prOpened);
        assertNull(prOpened.getBody());
        assertNull(prOpened.getAuthorAssociation());
    }

    @Test
    void testNormalizePrMergedEvent() {
        String json = "{\"action\":\"closed\",\"pull_request\":{\"id\":100,\"merged\":true},\"repository\":{\"id\":77}}";

        BaseEvent event = normalizer.normalize("github", "pull_request", 1, json);

        assertNotNull(event);
        assertInstanceOf(PrMergedEvent.class, event);
        assertEquals("pr.merged", event.getEventType());
    }

    @Test
    void testNormalizePrClosedEvent() {
        String json = "{\"action\":\"closed\",\"pull_request\":{\"id\":100,\"merged\":false},\"repository\":{\"id\":77}}";

        BaseEvent event = normalizer.normalize("github", "pull_request", 1, json);

        assertNotNull(event);
        assertInstanceOf(PrClosedEvent.class, event);
        assertEquals("pr.closed", event.getEventType());
    }

    @Test
    void testNormalizeCommitPushedEvent() {
        String json = "{\"head_commit\":{\"id\":\"abc123sha\",\"message\":\"fix: resolve bug\"},\"repository\":{\"id\":77},\"sender\":{\"id\":5}}";

        BaseEvent event = normalizer.normalize("github", "push", 1, json);

        assertNotNull(event);
        assertInstanceOf(CommitPushedEvent.class, event);
        CommitPushedEvent commitPushed = (CommitPushedEvent) event;
        assertEquals("commit.pushed", commitPushed.getEventType());
        assertEquals("abc123sha", commitPushed.getCommitSha());
        assertEquals("fix: resolve bug", commitPushed.getMessage());
    }

    @Test
    void testNormalizeDeploymentCreatedEvent() {
        String json = "{\"deployment\":{\"id\":500,\"sha\":\"def456sha\",\"environment\":\"staging\"},\"deployment_status\":{\"state\":\"success\"},\"repository\":{\"id\":77}}";

        BaseEvent event = normalizer.normalize("github", "deployment", 1, json);

        assertNotNull(event);
        assertInstanceOf(DeploymentCreatedEvent.class, event);
        DeploymentCreatedEvent depCreated = (DeploymentCreatedEvent) event;
        assertEquals("deployment.created", depCreated.getEventType());
        assertEquals("def456sha", depCreated.getCommitSha());
        assertEquals("staging", depCreated.getEnvironment());
    }

    // -- workflow_job (proxy for deployment events, see the normalizer's javadoc) --

    private static String workflowJobJson(String action, String jobName, String conclusion) {
        String conclusionField = conclusion == null ? "null" : "\"" + conclusion + "\"";
        return "{\"action\":\"" + action + "\",\"workflow_job\":{\"id\":900,"
                + "\"name\":\"" + jobName + "\",\"head_sha\":\"deploysha123\","
                + "\"conclusion\":" + conclusionField + "},\"repository\":{\"id\":77}}";
    }

    @Test
    void testWorkflowJobForTheDeployJobBecomesADeploymentCreatedEvent() {
        String json = workflowJobJson("completed", "Deploy to EC2", "success");

        BaseEvent event = normalizer.normalize("github", "workflow_job", 1, json);

        assertNotNull(event);
        assertInstanceOf(DeploymentCreatedEvent.class, event);
        DeploymentCreatedEvent deployment = (DeploymentCreatedEvent) event;
        assertEquals("deploysha123", deployment.getCommitSha());
        assertEquals("production", deployment.getEnvironment());
        assertEquals("success", deployment.getStatus());
    }

    @Test
    void testWorkflowJobForADifferentJobIsIgnored() {
        // "test" and "build-and-push" also fire this event; only "Deploy to EC2" deployed anything.
        String json = workflowJobJson("completed", "Test gate", "success");

        assertNull(normalizer.normalize("github", "workflow_job", 1, json));
    }

    @Test
    void testWorkflowJobStillRunningIsIgnored() {
        for (String action : new String[] {"queued", "in_progress"}) {
            String json = workflowJobJson(action, "Deploy to EC2", null);
            assertNull(normalizer.normalize("github", "workflow_job", 1, json),
                    "action=" + action + " should not report a deployment yet");
        }
    }

    @Test
    void testWorkflowJobSkippedMeansNothingWasDeployed() {
        // An earlier job (test/build-and-push) failed, so `deploy` never ran at all.
        String json = workflowJobJson("completed", "Deploy to EC2", "skipped");

        assertNull(normalizer.normalize("github", "workflow_job", 1, json));
    }

    @Test
    void testWorkflowJobFailureMapsToAStatusMetricsServiceAccepts() {
        String json = workflowJobJson("completed", "Deploy to EC2", "failure");

        DeploymentCreatedEvent deployment =
                (DeploymentCreatedEvent) normalizer.normalize("github", "workflow_job", 1, json);

        assertEquals("failure", deployment.getStatus());
    }

    @Test
    void testWorkflowJobCancelledOrTimedOutMapsToRolledBack() {
        for (String conclusion : new String[] {"cancelled", "timed_out"}) {
            String json = workflowJobJson("completed", "Deploy to EC2", conclusion);
            DeploymentCreatedEvent deployment =
                    (DeploymentCreatedEvent) normalizer.normalize("github", "workflow_job", 1, json);
            assertEquals("rolled_back", deployment.getStatus(), "conclusion=" + conclusion);
        }
    }

    @Test
    void testNormalizeJiraIssueUpdatedEvent() {
        String json = "{\"issue\":{\"id\":300,\"key\":\"DEV-99\",\"fields\":{\"summary\":\"Implement Auth API\",\"issuetype\":{\"name\":\"Story\"},\"priority\":{\"name\":\"High\"},\"status\":{\"name\":\"In Progress\"},\"customfield_10016\":5}}}";

        BaseEvent event = normalizer.normalize("jira", "jira:issue_updated", 1, json);

        assertNotNull(event);
        assertInstanceOf(IssueUpdatedEvent.class, event);
        IssueUpdatedEvent issueUpdated = (IssueUpdatedEvent) event;
        assertEquals("issue.updated", issueUpdated.getEventType());
        assertEquals("DEV-99", issueUpdated.getJiraKey());
        assertEquals("Implement Auth API", issueUpdated.getSummary());
        assertEquals(5, issueUpdated.getStoryPoints());
    }
}
