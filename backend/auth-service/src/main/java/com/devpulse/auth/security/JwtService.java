package com.devpulse.auth.security;

import com.devpulse.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for JWT token creation and validation.
 * <p>
 * Tokens are signed with HMAC-SHA256 using a secret configured via
 * {@code jwt.secret}. The payload contains:
 * <ul>
 *   <li>{@code sub} — user ID (integer, as string)</li>
 *   <li>{@code email} — user's email address</li>
 *   <li>{@code companyId} — tenant identifier</li>
 *   <li>{@code systemRole} — company-level role (admin / member)</li>
 * </ul>
 * Per-project roles are NOT embedded in the token — they are resolved
 * dynamically from the {@code project_members} table on each request.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:3600}") long expirationSeconds) {
        // Ensure the key is at least 256 bits for HMAC-SHA256
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationSeconds * 1000;
    }

    /**
     * Generates a signed JWT for the given user.
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("email", user.getEmail())
                .claim("companyId", user.getCompany() != null ? user.getCompany().getCompanyId() : null)
                .claim("systemRole", user.getSystemRole())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the token signature and expiry, then returns its claims.
     *
     * @throws JwtException if the token is invalid, expired, or tampered with
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the user ID (subject) from a validated token.
     */
    public Integer extractUserId(String token) {
        Claims claims = validateToken(token);
        return Integer.parseInt(claims.getSubject());
    }

    /**
     * Returns the token expiration time in seconds.
     */
    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }
}
