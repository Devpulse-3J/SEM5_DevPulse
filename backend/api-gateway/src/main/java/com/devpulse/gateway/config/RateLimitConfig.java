package com.devpulse.gateway.config;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /**
     * Decides what counts as one "caller" for rate limiting.
     *
     * <p>Authenticated requests are keyed by user, so one account cannot exhaust the budget of
     * another. {@code X-User-Id} is safe to trust here because JwtAuthenticationFilter (order
     * -100) strips any client-supplied copy and re-adds it from validated claims before this
     * runs — route filters run after global filters.
     *
     * <p>Public paths have no user yet, so they fall back to the caller's IP, which is also what
     * gives /api/auth/login its brute-force resistance.
     *
     * <p>Never returns an empty Mono: RequestRateLimiter rejects an empty key outright.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            String ip = (remote != null && remote.getAddress() != null)
                    ? remote.getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}
