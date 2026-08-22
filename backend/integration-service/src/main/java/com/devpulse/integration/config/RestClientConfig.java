package com.devpulse.integration.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Supplies the {@link RestTemplate} that {@code GithubApiClient} injects.
 * <p>
 * Spring Boot auto-configures a {@link RestTemplateBuilder}, but deliberately
 * never a {@code RestTemplate} bean — one is expected to be built from the
 * builder with the timeouts and interceptors the caller needs. Without this
 * class the whole context fails at startup with "No qualifying bean of type
 * RestTemplate", and the service crash-loops rather than serving anything.
 * <p>
 * The timeouts are the reason to build it here rather than {@code new
 * RestTemplate()}: the default has none at all, so a slow or hanging
 * api.github.com would tie up a request thread indefinitely.
 */
@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build();
    }
}
