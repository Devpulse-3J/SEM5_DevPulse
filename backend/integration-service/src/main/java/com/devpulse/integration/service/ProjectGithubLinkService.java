package com.devpulse.integration.service;

import com.devpulse.integration.client.GithubApiClient;
import com.devpulse.integration.dto.GithubStatusResponse;
import com.devpulse.integration.dto.LinkGithubRequest;
import com.devpulse.integration.dto.LinkGithubResponse;
import com.devpulse.integration.entity.Repo;
import com.devpulse.integration.exception.ApiException;
import com.devpulse.integration.github.GithubRepoUrlParser;
import com.devpulse.integration.github.GithubRepoUrlParser.GithubRepoCoordinates;
import com.devpulse.integration.repository.RepoRepository;
import com.devpulse.integration.security.ProjectAccessService;
import com.devpulse.integration.security.RequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links a project to a GitHub repository, and reports on that link.
 *
 * <p>Writes only {@code repos}, which integration-service owns. The
 * {@code projects} and {@code users} rows it consults are reads, through
 * {@link ProjectAccessService}.
 */
@Service
public class ProjectGithubLinkService {

    private static final Logger log = LoggerFactory.getLogger(ProjectGithubLinkService.class);

    private final RepoRepository repoRepository;
    private final GithubApiClient githubApiClient;
    private final ProjectAccessService projectAccessService;
    private final GithubHistoricalSyncService githubHistoricalSyncService;
    private final String webhookCallbackUrl;

    public ProjectGithubLinkService(
            RepoRepository repoRepository,
            GithubApiClient githubApiClient,
            ProjectAccessService projectAccessService,
            GithubHistoricalSyncService githubHistoricalSyncService,
            @Value("${devpulse.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.repoRepository = repoRepository;
        this.githubApiClient = githubApiClient;
        this.projectAccessService = projectAccessService;
        this.githubHistoricalSyncService = githubHistoricalSyncService;
        this.webhookCallbackUrl =
                publicBaseUrl.replaceAll("/+$", "") + "/api/webhooks/github";
    }

    @Transactional
    public LinkGithubResponse link(RequestContext context, Integer projectId,
                                   LinkGithubRequest request) {
        projectAccessService.requireAdminOnProject(context, projectId);

        GithubRepoCoordinates coordinates = GithubRepoUrlParser.parse(request.getRepoUrl())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "repoUrl must be a GitHub repository URL "
                                + "like https://github.com/owner/repo"));

        // One call: both the id and the default branch come from this response.
        JsonNode repoData = githubApiClient
                .fetchRepositoryDetails(coordinates.owner(), coordinates.repo());
        long githubRepoId = requireGithubRepoId(repoData, coordinates);
        String defaultBranch = repoData.path("default_branch").asText("main");

        // Refuse to silently swap a project's repository. Pull requests and
        // commits already ingested are attributed through repos.project_id, so
        // repointing would blend two repositories' history into one project.
        List<Repo> existingForProject =
                repoRepository.findByCompanyIdAndProjectId(context.companyId(), projectId);
        boolean conflictingRepo = existingForProject.stream()
                .anyMatch(repo -> !repo.getGithubRepoId().equals(githubRepoId));
        if (conflictingRepo) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This project is already linked to " + existingForProject.get(0).getFullName()
                            + ". Unlink it before linking a different repository.");
        }

        Repo repo = repoRepository
                .findByCompanyIdAndGithubRepoId(context.companyId(), githubRepoId)
                .orElseGet(() -> new Repo(
                        context.companyId(), projectId, githubRepoId,
                        coordinates.repo(), coordinates.owner(),
                        coordinates.fullName(), defaultBranch));

        // A repo row may already exist from a webhook delivery, where
        // project_id is left null. Linking is how it gets attached.
        repo.setProjectId(projectId);
        repo.setRepoName(coordinates.repo());
        repo.setOwnerName(coordinates.owner());
        repo.setFullName(coordinates.fullName());
        repo.setDefaultBranch(defaultBranch);
        if (request.getWebhookSecret() != null && !request.getWebhookSecret().isBlank()) {
            repo.setWebhookSecret(request.getWebhookSecret().trim());
        }

        Repo saved = repoRepository.save(repo);

        boolean webhookRegistered = githubApiClient.createWebhook(
                coordinates.owner(), coordinates.repo(),
                saved.getWebhookSecret(), webhookCallbackUrl);

        String webhookNote = webhookRegistered
                ? "Webhook registered on GitHub."
                : "Webhook NOT registered — add it manually at "
                        + coordinates.canonicalUrl() + "/settings/hooks pointing to "
                        + webhookCallbackUrl + " (content type application/json).";

        log.info("Admin user {} linked project {} in company {} to {} (github_repo_id={})",
                context.userId(), projectId, context.companyId(),
                coordinates.fullName(), githubRepoId);

        return LinkGithubResponse.from(saved, webhookRegistered, webhookNote);
    }

    @Transactional(readOnly = true)
    public GithubStatusResponse status(RequestContext context, Integer projectId) {
        projectAccessService.requireAdminOnProject(context, projectId);

        return repoRepository.findByCompanyIdAndProjectId(context.companyId(), projectId)
                .stream().findFirst()
                .map(GithubStatusResponse::connected)
                .orElseGet(() -> GithubStatusResponse.notLinked(projectId));
    }

    /**
     * Triggers the initial backfill.
     *
     * <p><b>Not implemented.</b> Per the agreed scope this logs the request and
     * returns without fetching anything. The working backfill already exists at
     * {@code POST /integrations/github/sync?owner=&repo=} — wiring this to
     * {@link GithubHistoricalSyncService#syncHistoricalProjectData} is a
     * one-line change once that endpoint's own tenancy handling is settled.
     *
     * <p>{@code last_synced_at} is deliberately NOT stamped: recording a sync
     * time for a sync that never ran would make the status endpoint lie.
     */
    public Map<String, Object> sync(RequestContext context, Integer projectId) {
        projectAccessService.requireAdminOnProject(context, projectId);

        Optional<Repo> linked = repoRepository
                .findByCompanyIdAndProjectId(context.companyId(), projectId)
                .stream().findFirst();

        if (linked.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This project has no linked repository. Link one before syncing.");
        }

        Repo repo = linked.get();
        log.info("Executing GitHub historical sync for project {} (company {}, repo {})...",
                projectId, context.companyId(), repo.getFullName());

        return githubHistoricalSyncService.syncHistoricalProjectData(
                repo.getOwnerName(), repo.getRepoName(), context.companyId(), projectId);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getConnectInfo(RequestContext context, Integer projectId) {
        String appName = System.getenv().getOrDefault("GITHUB_APP_NAME", "DevPulseIntegration");
        Optional<Repo> linked = repoRepository
                .findByCompanyIdAndProjectId(context.companyId(), projectId)
                .stream().findFirst();

        Map<String, String> info = new HashMap<>();
        info.put("connectUrl", "https://github.com/apps/" + appName + "/installations/new?state=" + projectId);

        if (linked.isPresent()) {
            Repo repo = linked.get();
            info.put("repoFullName", repo.getFullName());
            info.put("directWebhookUrl", "https://github.com/" + repo.getFullName() + "/settings/hooks/new");
        }
        return info;
    }

    /**
     * Resolves the numeric repository id GitHub assigns.
     *
     * <p>{@code GithubApiClient} swallows transport failures and returns an
     * empty node, so an absent id here means one of: the repo does not exist,
     * it is private and {@code github.api.token} is unset, or the rate limit is
     * exhausted. Any of those must fail the link — accepting it would write a
     * repo row with a fabricated id that no webhook could ever match.
     */
    private long requireGithubRepoId(JsonNode repoData, GithubRepoCoordinates coordinates) {
        long githubRepoId = repoData.path("id").asLong(0L);

        if (githubRepoId <= 0) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Could not read " + coordinates.fullName() + " from GitHub. "
                            + "Check the repository exists and, if it is private, that "
                            + "GITHUB_API_TOKEN is set.");
        }
        return githubRepoId;
    }
}
