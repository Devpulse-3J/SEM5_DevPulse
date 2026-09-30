package com.devpulse.integration.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.devpulse.integration.client.GithubApiClient;
import com.devpulse.integration.dto.LinkGithubRequest;
import com.devpulse.integration.entity.Repo;
import com.devpulse.integration.exception.ApiException;
import com.devpulse.integration.github.GithubAppClient;
import com.devpulse.integration.repository.RepoRepository;
import com.devpulse.integration.security.ProjectAccessService;
import com.devpulse.integration.security.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class ProjectGithubLinkServiceTest {

    private static final long GITHUB_REPO_ID = 1296161496L;
    private static final String REPO_URL = "https://github.com/Devpulse-3J/SEM5_DevPulse_Frontend";

    private RepoRepository repoRepository;
    private GithubApiClient githubApiClient;
    private GithubInstallationService installationService;
    private ProjectGithubLinkService service;
    private final RequestContext admin = new RequestContext(7, 12);

    @BeforeEach
    public void setUp() {
        repoRepository = mock(RepoRepository.class);
        githubApiClient = mock(GithubApiClient.class);
        installationService = mock(GithubInstallationService.class);
        service = new ProjectGithubLinkService(
                repoRepository, githubApiClient, mock(ProjectAccessService.class),
                mock(GithubHistoricalSyncService.class), installationService,
                "https://odineye.cse23.org", "odineye-integrator-v2");

        ObjectNode repoData = new ObjectMapper().createObjectNode();
        repoData.put("id", GITHUB_REPO_ID);
        repoData.put("default_branch", "main");
        when(githubApiClient.fetchRepositoryDetails("Devpulse-3J", "SEM5_DevPulse_Frontend")).thenReturn(repoData);
        when(repoRepository.findByCompanyIdAndProjectId(anyInt(), anyInt())).thenReturn(List.of());
        when(repoRepository.save(any(Repo.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Repo existingRepo(Integer projectId) {
        return new Repo(12, projectId, GITHUB_REPO_ID, "SEM5_DevPulse_Frontend", "Devpulse-3J",
                "Devpulse-3J/SEM5_DevPulse_Frontend", "main");
    }

    @Test
    public void refusesToMoveARepoAlreadyLinkedToAnotherProject() {
        when(repoRepository.findByCompanyIdAndGithubRepoId(12, GITHUB_REPO_ID))
                .thenReturn(Optional.of(existingRepo(5)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.link(admin, 19, new LinkGithubRequest(REPO_URL, null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(repoRepository, never()).save(any());
    }

    @Test
    public void attachesARepoThatNoProjectOwnsYet() {
        // Rows created by a webhook delivery before anyone linked the repo have no project.
        Repo orphan = existingRepo(null);
        when(repoRepository.findByCompanyIdAndGithubRepoId(12, GITHUB_REPO_ID)).thenReturn(Optional.of(orphan));

        service.link(admin, 19, new LinkGithubRequest(REPO_URL, null));

        assertEquals(19, orphan.getProjectId());
        verify(repoRepository).save(orphan);
    }

    @Test
    public void relinkingToTheSameProjectIsAllowed() {
        Repo repo = existingRepo(19);
        when(repoRepository.findByCompanyIdAndGithubRepoId(12, GITHUB_REPO_ID)).thenReturn(Optional.of(repo));

        assertDoesNotThrow(() -> service.link(admin, 19, new LinkGithubRequest(REPO_URL, null)));
        verify(repoRepository).save(repo);
    }

    @Test
    public void linksAPrivateRepoThroughAnInstallationToken() {
        // Unauthenticated lookup of a private repo returns nothing usable.
        when(githubApiClient.fetchRepositoryDetails("Org", "private-repo"))
                .thenReturn(new ObjectMapper().createArrayNode());
        ObjectNode privateRepo = new ObjectMapper().createObjectNode();
        privateRepo.put("id", 777L);
        when(installationService.tokenForRepository(12, "Org/private-repo")).thenReturn(Optional.of("ghs_token"));
        when(githubApiClient.fetchRepositoryDetails("Org", "private-repo", "ghs_token")).thenReturn(privateRepo);
        when(repoRepository.findByCompanyIdAndGithubRepoId(12, 777L)).thenReturn(Optional.empty());

        service.link(admin, 19, new LinkGithubRequest("https://github.com/Org/private-repo", null));

        verify(repoRepository).save(argThat(repo -> repo.getGithubRepoId() == 777L && repo.getProjectId() == 19));
    }

    @Test
    public void dropdownListsTheInstallationsReposNotThePersonalTokens() {
        when(installationService.hasInstallation(12)).thenReturn(true);
        when(installationService.listRepositories(12)).thenReturn(List.of(new GithubAppClient.InstallationRepo(
                GITHUB_REPO_ID, "SEM5_DevPulse_Frontend", "Devpulse-3J/SEM5_DevPulse_Frontend", REPO_URL, false)));
        when(repoRepository.findByCompanyId(12)).thenReturn(List.of());

        Map<String, Object> response = service.getAvailableRepositories(admin, 19);

        assertEquals(true, response.get("installed"));
        assertEquals("https://github.com/apps/odineye-integrator-v2/installations/new?state=19", response.get("connectUrl"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> repos = (List<Map<String, Object>>) response.get("repositories");
        assertEquals(1, repos.size());
        assertEquals(REPO_URL, repos.get(0).get("repoUrl"));
        verify(githubApiClient, never()).fetchUserRepositories();
    }
}
