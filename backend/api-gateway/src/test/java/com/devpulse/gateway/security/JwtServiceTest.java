package com.devpulse.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * {@link JwtService} is the gateway's only token verifier. These tests pin the
 * guarantees the rest of the security model rests on: a strong enough key is
 * required, and only a token signed with that exact key and still within its
 * expiry is accepted.
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-well-over-32-bytes";
    private final JwtService jwtService = new JwtService(SECRET);

    private static String token(String secret, String subject, Instant expiry) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    @Test
    void rejectsASecretShorterThan32Characters() {
        // HS256 with a short key is brute-forceable, so construction must fail loudly.
        assertThatThrownBy(() -> new JwtService("too-short"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsANullSecret() {
        assertThatThrownBy(() -> new JwtService(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsAValidTokenAndReturnsItsClaims() {
        var claims = jwtService.parseAndValidate(token(SECRET, "42", Instant.now().plusSeconds(3600)));
        assertThat(claims.getSubject()).isEqualTo("42");
    }

    @Test
    void extractsTheUserIdFromTheSubjectClaim() {
        assertThat(jwtService.extractUserId(token(SECRET, "42", Instant.now().plusSeconds(3600))))
                .isEqualTo(42L);
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        String forged = token("a-completely-different-secret-over-32-bytes", "42", Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> jwtService.parseAndValidate(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsATokenWhosePayloadWasTampered() {
        // Flip a character in the payload segment: the signature no longer matches.
        String valid = token(SECRET, "42", Instant.now().plusSeconds(3600));
        String[] parts = valid.split("\\.");
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

        assertThat(catchThrowable(() -> jwtService.parseAndValidate(tampered)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        String expired = token(SECRET, "42", Instant.now().minusSeconds(3600));
        assertThatThrownBy(() -> jwtService.parseAndValidate(expired))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAGarbageToken() {
        assertThatThrownBy(() -> jwtService.parseAndValidate("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void failsWhenTheSubjectIsNotNumeric() {
        String nonNumeric = token(SECRET, "not-a-number", Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> jwtService.extractUserId(nonNumeric))
                .isInstanceOf(NumberFormatException.class);
    }
}
