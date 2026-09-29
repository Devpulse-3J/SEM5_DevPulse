package com.devpulse.integration.jira;

import com.devpulse.integration.entity.JiraConnection;
import com.devpulse.integration.entity.JiraIssue;
import com.devpulse.integration.repository.JiraConnectionRepository;
import com.devpulse.integration.repository.JiraIssueRepository;
import com.devpulse.integration.repository.TenantAccessRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pulls issues from a connected Jira Cloud site via JQL search and upserts
 * them into {@code jira_issues}. Chosen over Jira's dynamic webhooks: OAuth
 * 2.0 app webhooks authenticate with a bearer JWT signed by Atlassian that
 * this service would need to verify, and they expire every 30 days needing
 * active re-registration — both solvable, but a pull keeps the integration
 * self-contained and doesn't silently go stale.
 */
@Service
public class JiraIssueSyncService {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueSyncService.class);
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 5; // caps a single sync run at 500 issues

    private final JiraCloudClient client;
    private final JiraConnectionRepository connectionRepository;
    private final JiraIssueRepository issueRepository;
    private final TenantAccessRepository tenantAccessRepository;

    public JiraIssueSyncService(
            JiraCloudClient client,
            JiraConnectionRepository connectionRepository,
            JiraIssueRepository issueRepository,
            TenantAccessRepository tenantAccessRepository) {
        this.client = client;
        this.connectionRepository = connectionRepository;
        this.issueRepository = issueRepository;
        this.tenantAccessRepository = tenantAccessRepository;
    }

    /** Pulls up to {@code MAX_PAGES * PAGE_SIZE} issues and upserts them. Returns how many were written. */
    public int sync(JiraConnection connection) {
        int upserted = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            int startAt = page * PAGE_SIZE;
            List<JiraCloudClient.JiraIssueData> batch = searchWithRefresh(connection, startAt);
            if (batch.isEmpty()) {
                break;
            }
            for (JiraCloudClient.JiraIssueData data : batch) {
                if (upsert(connection.getCompanyId(), data)) {
                    upserted++;
                }
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }
        return upserted;
    }

    private List<JiraCloudClient.JiraIssueData> searchWithRefresh(JiraConnection connection, int startAt) {
        try {
            return client.searchIssues(connection.getCloudId(), connection.getAccessToken(), startAt, PAGE_SIZE);
        } catch (JiraCloudClient.JiraApiException e) {
            if (!JiraCloudClient.isUnauthorized(e) || connection.getRefreshToken() == null) {
                throw e;
            }
            log.info("Jira access token expired for company {}, refreshing", connection.getCompanyId());
            JiraCloudClient.TokenResult refreshed = client.refreshAccessToken(connection.getRefreshToken());
            connection.setAccessToken(refreshed.accessToken());
            connection.setRefreshToken(refreshed.refreshToken());
            connection.setUpdatedAt(Instant.now());
            connectionRepository.save(connection);
            return client.searchIssues(connection.getCloudId(), connection.getAccessToken(), startAt, PAGE_SIZE);
        }
    }

    private boolean upsert(Integer companyId, JiraCloudClient.JiraIssueData data) {
        if (data.key() == null || data.key().isBlank()) {
            return false;
        }
        Integer projectId = tenantAccessRepository.findProjectIdByJiraKey(companyId, data.projectKey()).orElse(null);
        Integer assigneeId = tenantAccessRepository.findUserIdByEmail(companyId, data.assigneeEmail()).orElse(null);

        JiraIssue issue = issueRepository.findByCompanyIdAndJiraKey(companyId, data.key())
                .orElseGet(() -> new JiraIssue(companyId, projectId, data.key(), data.summary(), data.issueType(),
                        data.priority(), data.status(), data.storyPoints(), assigneeId));

        issue.setProjectId(projectId);
        issue.setSummary(data.summary());
        issue.setIssueType(data.issueType());
        issue.setPriority(data.priority());
        issue.setStatus(data.status());
        issue.setStoryPoints(data.storyPoints());
        issue.setAssigneeId(assigneeId);
        if (data.done() && issue.getClosedAt() == null) {
            issue.setClosedAt(Instant.now());
        }

        issueRepository.save(issue);
        return true;
    }
}
