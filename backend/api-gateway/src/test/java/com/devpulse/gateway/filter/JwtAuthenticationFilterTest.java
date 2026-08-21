package com.devpulse.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Edge authentication tests for the gateway.
 *
 * <p>These assert the behaviour of {@link JwtAuthenticationFilter}, which runs
 * at order -100 — well before routing at 10000. That ordering is what makes
 * the tests possible without any downstream service running: an unauthenticated
 * request is rejected by the filter and never proxied anywhere.
 *
 * <p>Note the route choice. Spring Cloud Gateway runs global filters only for
 * <em>matched</em> routes, so these use {@code /api/alerts/**}, which is an
 * active route in application.yml. A path with no route (for example
 * {@code /api/metrics/**}, still commented out) would 404 before the filter
 * ever ran, and would prove nothing about authentication.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class JwtAuthenticationFilterTest {

    /** Must match devpulse.gateway.jwt.secret in application-test.yml. */
    private static final String TEST_SECRET =
            "test-only-secret-key-min-32-chars-long-not-a-real-secret";

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Mints a token the gateway will accept as genuine. HS256 is symmetric, so
     * signing with the same secret the service was configured with is exactly
     * what auth-service does in production.
     */
    private static String signedToken(Instant expiresAt) {
        return Jwts.builder()
                .subject("42")                  // DevPulse carries the user id in `sub`
                .claim("companyId", 7L)
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void protectedRouteWithoutTokenIsUnauthorized() {
        webTestClient.get()
                .uri("/api/alerts/rules")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteWithMalformedTokenIsUnauthorized() {
        webTestClient.get()
                .uri("/api/alerts/rules")
                .header("Authorization", "Bearer not-a-real-jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteWithWrongSchemeIsUnauthorized() {
        webTestClient.get()
                .uri("/api/alerts/rules")
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /**
     * The 401 body must be the one shaped by GlobalExceptionHandler. A filter
     * that ended the exchange with setStatusCode + setComplete would return an
     * empty body and bypass that handler entirely, so this asserts the error
     * contract rather than only the status code.
     */
    @Test
    void unauthorizedResponseUsesTheJsonErrorContract() {
        webTestClient.get()
                .uri("/api/alerts/rules")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").exists()
                .jsonPath("$.path").isEqualTo("/api/alerts/rules");
    }

    /**
     * A correctly signed, unexpired token must get past the filter.
     *
     * <p>The assertion is deliberately "not 401" rather than a specific success
     * status. Once the filter admits the request, routing forwards it to
     * notification-service, which is not running in this test — so the response
     * is a downstream failure. That is fine: the subject under test is the
     * filter's admit/reject decision, and any status other than 401 proves it
     * admitted the request rather than rejecting the credentials.
     */
    @Test
    void validTokenIsAdmittedByTheFilter() {
        webTestClient.get()
                .uri("/api/alerts/rules")
                .header("Authorization", "Bearer " + signedToken(Instant.now().plusSeconds(3600)))
                .exchange()
                .expectStatus().value(status ->
                        assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    /**
     * Same signature, same secret — only the expiry differs. JwtService calls
     * parseSignedClaims, which enforces exp, so this must be rejected.
     */
    @Test
    void expiredTokenIsUnauthorized() {
        webTestClient.get()
                .uri("/api/alerts/rules")
                .header("Authorization", "Bearer " + signedToken(Instant.now().minusSeconds(3600)))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
