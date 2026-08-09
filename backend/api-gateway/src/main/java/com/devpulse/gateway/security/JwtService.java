package com.devpulse.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtService {

    private final SecretKey signingKey;

    public JwtService(@Value("${devpulse.gateway.jwt.secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "jwt.secret must be at least 32 chars. Got length: " +
                (secret == null ? "null" : secret.length())
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses the token, verifies signature and expiry.
     * Throws JwtException if invalid.
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * The DevPulse JWT contract carries the user id in the standard `sub` claim.
     */
    public Long extractUserId(String token) {
        return Long.valueOf(parseAndValidate(token).getSubject());
    }
}