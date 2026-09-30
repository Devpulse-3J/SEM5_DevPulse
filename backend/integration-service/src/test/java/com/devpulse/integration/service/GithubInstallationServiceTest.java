package com.devpulse.integration.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.devpulse.integration.entity.GithubInstallation;
import com.devpulse.integration.exception.ApiException;
import com.devpulse.integration.github.GithubAppClient;
import com.devpulse.integration.github.GithubAppClient.Installation;
import com.devpulse.integration.github.GithubAppClient.InstallationRepo;
import com.devpulse.integration.repository.GithubInstallationRepository;
import com.devpulse.integration.security.ProjectAccessService;
import com.devpulse.integration.security.RequestContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class GithubInstallationServiceTest {

    private GithubInstallationRepository repository;
    private GithubAppClient appClient;
    private ProjectAccessService projectAccessService;
    private GithubInstallationService service;
    private final RequestContext admin = new RequestContext(7, 12);

    @BeforeEach
    public void setUp() {
        repository = mock(GithubInstallationRepository.class);
        appClient = mock(GithubAppClient.class);
        projectAccessService = mock(ProjectAccessService.class);
        service = new GithubInstallationService(repository, appClient, projectAccessService);

        when(appClient.isConfigured()).thenReturn(true);
        when(repository.save(any(GithubInstallation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void claimsAnInstallationOfThisAppForTheCallersCompany() {
        when(appClient.findInstallation(555L)).thenReturn(Optional.of(new Installation(555L, "Devpulse-3J", "Organization")));
        when(repository.findById(555L)).thenReturn(Optional.empty());

        GithubInstallation saved = service.claim(admin, 19, 555L);

        verify(projectAccessService).requireAdminOnProject(admin, 19);
        assertEquals(12, saved.getCompanyId());
        assertEquals("Devpulse-3J", saved.getAccountLogin());
    }

    @Test
    public void rejectsAnIdThatIsNotAnInstallationOfThisApp() {
        when(appClient.findInstallation(999L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.claim(admin, 19, 999L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    public void refusesAnInstallationAnotherCompanyAlreadyClaimed() {
        when(appClient.findInstallation(555L)).thenReturn(Optional.of(new Installation(555L, "someone-else", "Organization")));
        when(repository.findById(555L)).thenReturn(Optional.of(new GithubInstallation(555L, 99)));

        ApiException ex = assertThrows(ApiException.class, () -> service.claim(admin, 19, 555L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    public void refusesToClaimWhenTheAppIsNotConfigured() {
        when(appClient.isConfigured()).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.claim(admin, 19, 555L));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    public void listsReposAcrossInstallationsAndSkipsOneThatFails() {
        when(repository.findByCompanyId(12)).thenReturn(List.of(
                new GithubInstallation(1L, 12), new GithubInstallation(2L, 12), new GithubInstallation(3L, 12)));
        when(appClient.listRepositories(1L)).thenReturn(List.of(
                new InstallationRepo(10L, "a", "Org/a", "https://github.com/Org/a", false)));
        when(appClient.listRepositories(2L)).thenThrow(new RuntimeException("installation revoked"));
        when(appClient.listRepositories(3L)).thenReturn(List.of(
                new InstallationRepo(10L, "a", "org/A", "https://github.com/Org/a", false),
                new InstallationRepo(11L, "b", "Org/b", "https://github.com/Org/b", true)));

        List<InstallationRepo> repos = service.listRepositories(12);

        assertEquals(List.of("Org/a", "Org/b"), repos.stream().map(InstallationRepo::fullName).toList());
    }

    @Test
    public void findsATokenOnlyFromAnInstallationThatCoversTheRepo() {
        when(repository.findByCompanyId(12)).thenReturn(List.of(new GithubInstallation(1L, 12), new GithubInstallation(2L, 12)));
        when(appClient.listRepositories(1L)).thenReturn(List.of(
                new InstallationRepo(10L, "a", "Org/a", "https://github.com/Org/a", false)));
        when(appClient.listRepositories(2L)).thenReturn(List.of(
                new InstallationRepo(11L, "private", "Org/private", "https://github.com/Org/private", true)));
        when(appClient.installationToken(2L)).thenReturn("ghs_installation2");

        assertEquals(Optional.of("ghs_installation2"), service.tokenForRepository(12, "org/PRIVATE"));
        assertEquals(Optional.empty(), service.tokenForRepository(12, "Org/unknown"));
        verify(appClient, never()).installationToken(1L);
    }
}
