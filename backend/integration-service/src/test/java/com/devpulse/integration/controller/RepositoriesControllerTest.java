package com.devpulse.integration.controller;

import com.devpulse.integration.dto.RepositoryDto;
import com.devpulse.integration.entity.Repo;
import com.devpulse.integration.exception.ApiException;
import com.devpulse.integration.repository.RepoRepository;
import com.devpulse.integration.security.RequestContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Plain unit test with a real {@link RequestContextResolver}, matching the style
 * of {@link GithubSyncControllerTest}. The resolver is exercised for real so the
 * missing-header case tests the actual header contract rather than a stub.
 */
public class RepositoriesControllerTest {

    private static final Integer CALLER_COMPANY = 7;
    private static final Integer OTHER_COMPANY = 99;

    private RepoRepository repoRepository;
    private RepositoriesController controller;

    @BeforeEach
    public void setUp() {
        repoRepository = mock(RepoRepository.class);
        controller = new RepositoriesController(repoRepository, new RequestContextResolver());
    }

    /** A request carrying the identity headers the gateway sets. */
    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "3");
        request.addHeader("X-Company-Id", String.valueOf(CALLER_COMPANY));
        return request;
    }

    private Repo repo(Integer repoId, Integer companyId) {
        Repo repo = new Repo(companyId, 4, 123456789L,
                "Sem5_DevPulse_Frontend", "Devpulse-3J",
                "Devpulse-3J/Sem5_DevPulse_Frontend", "main");
        repo.setRepoId(repoId);
        repo.setWebhookSecret("super-secret-value");
        return repo;
    }

    @Test
    public void listReturnsOnlyTheCallersCompanyRepos() {
        when(repoRepository.findByCompanyId(CALLER_COMPANY))
                .thenReturn(List.of(repo(1, CALLER_COMPANY), repo(2, CALLER_COMPANY)));

        ResponseEntity<List<RepositoryDto>> response = controller.list(authenticatedRequest());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        assertEquals("Devpulse-3J/Sem5_DevPulse_Frontend", response.getBody().get(0).getFullName());
        // The company id came from the header, never from the caller's choice.
        verify(repoRepository).findByCompanyId(CALLER_COMPANY);
        verifyNoMoreInteractions(repoRepository);
    }

    @Test
    public void listWithoutCompanyHeaderIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "3"); // company header deliberately absent

        ApiException thrown = assertThrows(ApiException.class, () -> controller.list(request));

        // RequestContextResolver treats a missing gateway identity header as
        // unauthenticated, consistently with every other endpoint here.
        assertEquals(HttpStatus.UNAUTHORIZED, thrown.getStatus());
        verifyNoInteractions(repoRepository);
    }

    @Test
    public void getReturnsRepoInTheSameCompany() {
        when(repoRepository.findByRepoIdAndCompanyId(1, CALLER_COMPANY))
                .thenReturn(Optional.of(repo(1, CALLER_COMPANY)));

        ResponseEntity<RepositoryDto> response = controller.get(authenticatedRequest(), 1);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getId());
        assertEquals(123456789L, response.getBody().getGithubRepoId());
    }

    @Test
    public void getRepoFromAnotherCompanyIsNotFound() {
        // The repo exists, but not for this company — the scoped finder misses it.
        when(repoRepository.findByRepoIdAndCompanyId(42, CALLER_COMPANY))
                .thenReturn(Optional.empty());

        ApiException thrown = assertThrows(ApiException.class,
                () -> controller.get(authenticatedRequest(), 42));

        assertEquals(HttpStatus.NOT_FOUND, thrown.getStatus());
        verify(repoRepository).findByRepoIdAndCompanyId(42, CALLER_COMPANY);
    }

    @Test
    public void webhookSecretIsNeverSerialised() throws Exception {
        when(repoRepository.findByCompanyId(CALLER_COMPANY))
                .thenReturn(List.of(repo(1, CALLER_COMPANY)));

        ResponseEntity<List<RepositoryDto>> response = controller.list(authenticatedRequest());
        String json = new ObjectMapper().writeValueAsString(response.getBody());

        assertFalse(json.contains("super-secret-value"), "secret value leaked into the response");
        assertFalse(json.contains("webhookSecret\""), "webhookSecret field leaked into the response");
        // Only the derived boolean is exposed.
        assertTrue(json.contains("webhookSecretConfigured"));
        assertTrue(response.getBody().get(0).isWebhookSecretConfigured());
    }

    @Test
    public void repoWithoutASecretReportsNotConfigured() {
        Repo repo = repo(1, CALLER_COMPANY);
        repo.setWebhookSecret(null);
        when(repoRepository.findByCompanyId(CALLER_COMPANY)).thenReturn(List.of(repo));

        ResponseEntity<List<RepositoryDto>> response = controller.list(authenticatedRequest());

        assertFalse(response.getBody().get(0).isWebhookSecretConfigured());
    }

    @Test
    public void otherCompanyReposAreNeverQueried() {
        when(repoRepository.findByCompanyId(CALLER_COMPANY)).thenReturn(List.of());

        ResponseEntity<List<RepositoryDto>> response = controller.list(authenticatedRequest());

        assertTrue(response.getBody().isEmpty());
        verify(repoRepository, never()).findByCompanyId(OTHER_COMPANY);
        verify(repoRepository, never()).findAll();
    }
}
