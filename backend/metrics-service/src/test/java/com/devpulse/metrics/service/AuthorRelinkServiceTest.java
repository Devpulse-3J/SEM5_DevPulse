package com.devpulse.metrics.service;

import com.devpulse.metrics.dto.RelinkResponse;
import com.devpulse.metrics.exception.ApiException;
import com.devpulse.metrics.repository.AuthorRelinkRepository;
import com.devpulse.metrics.security.ProjectAccessService;
import com.devpulse.metrics.security.RequestContext;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Catching up the caller's own earlier PRs once their GitHub account is known. */
public class AuthorRelinkServiceTest {

    private ProjectAccessService accessService;
    private AuthorRelinkRepository repository;
    private AuthorRelinkService service;
    private final RequestContext context = new RequestContext(22, 15);

    @BeforeEach
    public void setUp() {
        accessService = mock(ProjectAccessService.class);
        repository = mock(AuthorRelinkRepository.class);
        service = new AuthorRelinkService(accessService, repository);
    }

    @Test
    public void attributesEarlierPullRequestsForTheCallersGithubAccount() {
        when(repository.findGithubId(22)).thenReturn(Optional.of(197457783L));
        when(repository.relinkPullRequests(15, 22, 197457783L)).thenReturn(5);

        RelinkResponse response = service.relinkMyPullRequests(context);

        assertEquals(5, response.linkedPullRequests());
    }

    @Test
    public void doesNothingWhenNoGithubAccountIsLinked() {
        when(repository.findGithubId(22)).thenReturn(Optional.empty());

        RelinkResponse response = service.relinkMyPullRequests(context);

        assertEquals(0, response.linkedPullRequests());
        verify(repository, never()).relinkPullRequests(anyInt(), anyInt(), anyLong());
    }

    @Test
    public void onlyActsForACallerWhoBelongsToTheCompany() {
        doThrow(new ApiException(HttpStatus.UNAUTHORIZED, "USER_CONTEXT_NOT_FOUND", "not in company"))
                .when(accessService).requireCompanyAccess(context);

        assertThrows(ApiException.class, () -> service.relinkMyPullRequests(context));
        verifyNoInteractions(repository);
    }

    @Test
    public void alwaysScopesTheUpdateToTheCallersOwnCompanyAndUser() {
        when(repository.findGithubId(22)).thenReturn(Optional.of(1L));
        when(repository.relinkPullRequests(anyInt(), anyInt(), anyLong())).thenReturn(0);

        service.relinkMyPullRequests(context);

        verify(repository).relinkPullRequests(15, 22, 1L);
    }
}
