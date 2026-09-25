package com.devpulse.auth.service;

import com.devpulse.auth.dto.LinkGithubResponse;
import com.devpulse.auth.entity.User;
import com.devpulse.auth.exception.ConflictException;
import com.devpulse.auth.exception.ExternalServiceException;
import com.devpulse.auth.exception.ResourceNotFoundException;
import com.devpulse.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Linking a GitHub username saves that account's numeric id on the user - the
 * id metrics-service matches pull request and commit authors against.
 */
public class GithubIdentityServiceTest {

    private UserRepository userRepository;
    private GithubUserLookup lookup;
    private GithubIdentityService service;
    private User user;

    @BeforeEach
    public void setUp() {
        userRepository = mock(UserRepository.class);
        lookup = mock(GithubUserLookup.class);
        service = new GithubIdentityService(userRepository, lookup);

        user = new User();
        user.setUserId(24);
        when(userRepository.findById(24)).thenReturn(Optional.of(user));
        when(userRepository.findFirstByGithubId(anyLong())).thenReturn(Optional.empty());
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    @Test
    public void savesTheNumericGithubIdOnTheUser() {
        when(lookup.findByUsername("UmayaJayasuriya"))
                .thenReturn(Optional.of(new GithubUserLookup.GithubAccount(194699006L, "UmayaJayasuriya")));

        LinkGithubResponse response = service.link(24, "UmayaJayasuriya");

        assertEquals(194699006L, user.getGithubId());
        assertEquals(194699006L, response.getGithubId());
        assertEquals("UmayaJayasuriya", response.getGithubLogin());
        verify(userRepository).save(user);
    }

    @Test
    public void trimsTheUsernameBeforeLookingItUp() {
        when(lookup.findByUsername("octocat"))
                .thenReturn(Optional.of(new GithubUserLookup.GithubAccount(583231L, "octocat")));

        service.link(24, "  octocat ");

        verify(lookup).findByUsername("octocat");
    }

    @Test
    public void anUnknownGithubUsernameIsNotFound() {
        when(lookup.findByUsername("no-such-user-xyz")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.link(24, "no-such-user-xyz"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    public void aGithubAccountAlreadyLinkedToSomeoneElseIsRefusedNotOverwritten() {
        User owner = new User();
        owner.setUserId(99);
        when(lookup.findByUsername("taken"))
                .thenReturn(Optional.of(new GithubUserLookup.GithubAccount(555L, "taken")));
        when(userRepository.findFirstByGithubId(555L)).thenReturn(Optional.of(owner));

        assertThrows(ConflictException.class, () -> service.link(24, "taken"));
        assertNull(user.getGithubId());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    public void relinkingTheAccountYouAlreadyOwnIsFine() {
        user.setGithubId(555L);
        when(lookup.findByUsername("mine"))
                .thenReturn(Optional.of(new GithubUserLookup.GithubAccount(555L, "mine")));
        when(userRepository.findFirstByGithubId(555L)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> service.link(24, "mine"));
    }

    @Test
    public void githubBeingUnreachableIsReportedNotSwallowed() {
        when(lookup.findByUsername("anyone"))
                .thenThrow(new ExternalServiceException("Could not reach GitHub", new RuntimeException()));

        assertThrows(ExternalServiceException.class, () -> service.link(24, "anyone"));
        verify(userRepository, never()).save(any(User.class));
    }
}
