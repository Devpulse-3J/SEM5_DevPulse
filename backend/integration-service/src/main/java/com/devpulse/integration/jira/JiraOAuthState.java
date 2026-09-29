package com.devpulse.integration.jira;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signs and verifies the OAuth {@code state} parameter carrying the caller's
 * company/user id through the Jira 3LO round trip.
 *
 * <p>{@code /api/integrations/jira/oauth/} is a public gateway path — Atlassian's
 * redirect back to {@code /oauth/callback} is a plain browser navigation with no
 * Authorization header, so the gateway never sets {@code X-Company-Id} on it (see
 * JwtAuthenticationFilter, which strips those headers on every request, public
 * paths included). {@code state} is the only channel left to carry which company
 * initiated the connection, so it is HMAC-signed with the Jira app's own client
 * secret and time-boxed to reject anything replayed or tampered with.
 */
@Component
public class JiraOAuthState {

    private static final long MAX_AGE_SECONDS = 15 * 60;

    @Value("${ATLASSIAN_CLIENT_SECRET:}")
    private String signingKey;

    public record Claims(Integer companyId, Integer userId) {
    }

    public String sign(Integer companyId, Integer userId) {
        long timestamp = Instant.now().getEpochSecond();
        String payload = companyId + "." + (userId != null ? userId : 0) + "." + timestamp;
        String signature = hmac(payload);
        String token = payload + "." + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    /** Empty when the state is missing, malformed, expired, or fails signature verification. */
    public java.util.Optional<Claims> verify(String state) {
        if (state == null || state.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\.", 4);
            if (parts.length != 4) {
                return java.util.Optional.empty();
            }
            String payload = parts[0] + "." + parts[1] + "." + parts[2];
            String expectedSignature = hmac(payload);
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[3].getBytes(StandardCharsets.UTF_8))) {
                return java.util.Optional.empty();
            }
            long timestamp = Long.parseLong(parts[2]);
            if (Instant.now().getEpochSecond() - timestamp > MAX_AGE_SECONDS) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new Claims(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            String key = (signingKey != null && !signingKey.isBlank()) ? signingKey : "devpulse-jira-state-fallback";
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign OAuth state", e);
        }
    }
}
