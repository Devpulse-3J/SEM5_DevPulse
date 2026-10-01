package com.devpulse.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpulse.gateway.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Identity-header handling in {@link JwtAuthenticationFilter}, tested against
 * the filter directly with a capturing chain so the request that WOULD be
 * forwarded downstream can be inspected without any service running.
 *
 * <p>The property under test is the core trust boundary of the whole system:
 * downstream services trust {@code X-User-Id} / {@code X-Company-Id}, so the
 * gateway must drop whatever a client sent under those names and set them only
 * from a validated token. A regression here would let any caller impersonate
 * any user or tenant by sending a header.
 */
class JwtAuthenticationFilterHeaderTest {

    /** Matches the secret the gateway is configured with in these tests. */
    private static final String SECRET = "test-only-secret-key-min-32-chars-long-not-a-real-secret";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String COMPANY_ID_HEADER = "X-Company-Id";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new JwtService(SECRET));

    /** The exchange the filter hands to the chain, or null if it rejected the request. */
    private ServerWebExchange forwarded;

    private final GatewayFilterChain capturingChain = exchange -> {
        this.forwarded = exchange;
        return Mono.empty();
    };

    private static String tokenFor(String subject, Long companyId) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(Instant.now().plusSeconds(3600)));
        if (companyId != null) {
            builder.claim("companyId", companyId);
        }
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test
    void replacesAClientSuppliedUserIdWithTheOneFromTheToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/alerts/rules")
                .header("Authorization", "Bearer " + tokenFor("42", 7L))
                .header(USER_ID_HEADER, "999999")        // forged: a different user
                .header(COMPANY_ID_HEADER, "888888")     // forged: a different tenant
                .build();

        filter.filter(MockServerWebExchange.from(request), capturingChain).block();

        assertThat(forwarded).as("valid token must be admitted").isNotNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst(USER_ID_HEADER)).isEqualTo("42");
        assertThat(forwarded.getRequest().getHeaders().getFirst(COMPANY_ID_HEADER)).isEqualTo("7");
    }

    @Test
    void setsAnEmptyCompanyIdWhenTheTokenHasNoCompanyClaim() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/alerts/rules")
                .header("Authorization", "Bearer " + tokenFor("42", null))
                .header(COMPANY_ID_HEADER, "888888")     // forged, must not survive
                .build();

        filter.filter(MockServerWebExchange.from(request), capturingChain).block();

        assertThat(forwarded.getRequest().getHeaders().getFirst(USER_ID_HEADER)).isEqualTo("42");
        // Empty string, deliberately, so downstream never sees the forged value.
        assertThat(forwarded.getRequest().getHeaders().getFirst(COMPANY_ID_HEADER)).isEmpty();
    }

    @Test
    void stripsForgedIdentityHeadersEvenOnAPublicPathWithNoToken() {
        // A public path is not authenticated, so forged identity headers must be
        // removed outright rather than replaced - otherwise the "public" GitHub
        // OAuth callback would be an impersonation hole.
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header(USER_ID_HEADER, "999999")
                .header(COMPANY_ID_HEADER, "888888")
                .build();

        filter.filter(MockServerWebExchange.from(request), capturingChain).block();

        assertThat(forwarded).as("public path must be admitted").isNotNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst(USER_ID_HEADER)).isNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst(COMPANY_ID_HEADER)).isNull();
    }

    @Test
    void stripsForgedIdentityHeadersOnARejectedRequest() {
        // Even though the request is rejected, the chain must never be reached,
        // so no forged header can leak through on the error path either.
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/alerts/rules")
                .header(USER_ID_HEADER, "999999")
                .build();

        assertThatThrownBy(() -> filter.filter(MockServerWebExchange.from(request), capturingChain).block())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        assertThat(forwarded).as("rejected request must not reach the chain").isNull();
    }

    @Test
    void doesNotAdmitATokenSignedWithADifferentSecret() {
        String forgedKeyToken = Jwts.builder()
                .subject("42")
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(
                        "a-totally-different-secret-also-32-bytes-long!!".getBytes(StandardCharsets.UTF_8)))
                .compact();
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/alerts/rules")
                .header("Authorization", "Bearer " + forgedKeyToken)
                .build();

        assertThatThrownBy(() -> filter.filter(MockServerWebExchange.from(request), capturingChain).block())
                .isInstanceOf(ResponseStatusException.class);
        assertThat(forwarded).isNull();
    }

    @Test
    void admitsACorsPreflightWithoutATokenButStillStripsIdentityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/alerts/rules")
                .header(USER_ID_HEADER, "999999")
                .build();

        filter.filter(MockServerWebExchange.from(request), capturingChain).block();

        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst(USER_ID_HEADER)).isNull();
    }
}
