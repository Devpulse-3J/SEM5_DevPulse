package com.devpulse.auth.service;

import com.devpulse.auth.exception.ExternalServiceException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Looks a username up on GitHub's public API ({@code GET /users/{login}}).
 * Unauthenticated, so it is subject to GitHub's per-IP rate limit; that is
 * plenty for the occasional "link my GitHub account" click.
 */
@Component
public class GithubApiUserLookup implements GithubUserLookup {

    private final RestClient client;

    public GithubApiUserLookup() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        this.client = RestClient.builder()
                .baseUrl("https://api.github.com")
                .requestFactory(factory)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("User-Agent", "devpulse-auth-service")
                .build();
    }

    @Override
    public Optional<GithubAccount> findByUsername(String username) {
        try {
            GithubUser user = client.get()
                    .uri("/users/{login}", username)
                    .retrieve()
                    .body(GithubUser.class);
            if (user == null || user.id() == null || user.login() == null) {
                return Optional.empty();
            }
            return Optional.of(new GithubAccount(user.id(), user.login()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            throw new ExternalServiceException(
                    "GitHub refused the lookup (" + status.value() + "); try again in a few minutes", e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Could not reach GitHub; try again shortly", e);
        }
    }

    private record GithubUser(Long id, String login) {
    }
}
