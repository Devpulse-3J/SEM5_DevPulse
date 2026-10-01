package com.devpulse.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * The rate-limit key decides whose budget a request spends. Getting it wrong is
 * a security problem, not only a performance one: if every request keyed to the
 * same value, one caller could exhaust everyone's budget (a denial of service),
 * and if the per-IP fallback returned an empty key the limiter would reject the
 * request outright.
 */
class RateLimitConfigTest {

    private final KeyResolver resolver = new RateLimitConfig().userKeyResolver();

    @Test
    void keysAuthenticatedRequestsByUserSoOneAccountCannotSpendAnothersBudget() {
        // X-User-Id is set by the JWT filter from validated claims before this runs.
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/alerts/rules")
                .header("X-User-Id", "42")
                .build();

        String key = resolver.resolve(MockServerWebExchange.from(request)).block();

        assertThat(key).isEqualTo("user:42");
    }

    @Test
    void fallsBackToTheCallersIpWhenThereIsNoUser() {
        // Public paths such as /api/auth/login have no user yet; keying by IP is
        // what gives login its brute-force resistance.
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new InetSocketAddress("203.0.113.9", 54321))
                .build();

        String key = resolver.resolve(MockServerWebExchange.from(request)).block();

        assertThat(key).isEqualTo("ip:203.0.113.9");
    }

    @Test
    void treatsABlankUserIdAsNoUser() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header("X-User-Id", "   ")
                .remoteAddress(new InetSocketAddress("203.0.113.9", 54321))
                .build();

        String key = resolver.resolve(MockServerWebExchange.from(request)).block();

        assertThat(key).isEqualTo("ip:203.0.113.9");
    }

    @Test
    void neverResolvesToAnEmptyKeyWhichTheLimiterWouldReject() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/alerts/rules").build();

        String key = resolver.resolve(MockServerWebExchange.from(request)).block();

        assertThat(key).isNotBlank();
    }
}
