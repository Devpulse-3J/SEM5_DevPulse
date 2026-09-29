package com.devpulse.integration.jira;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JiraOAuthStateTest {

    private JiraOAuthState state;

    @BeforeEach
    public void setUp() throws Exception {
        state = new JiraOAuthState();
        Field field = JiraOAuthState.class.getDeclaredField("signingKey");
        field.setAccessible(true);
        field.set(state, "test-atlassian-client-secret");
    }

    @Test
    public void roundTripsCompanyAndUserId() {
        String token = state.sign(42, 7);
        var claims = state.verify(token);
        assertTrue(claims.isPresent());
        assertEquals(42, claims.get().companyId());
        assertEquals(7, claims.get().userId());
    }

    @Test
    public void rejectsTamperedPayload() {
        String token = state.sign(42, 7);
        // Flip the signed company id without re-signing — must fail closed.
        String forged = state.sign(999, 7);
        assertNotEquals(token, forged);
        assertEquals(42, state.verify(token).get().companyId());
        assertEquals(999, state.verify(forged).get().companyId());

        String tamperedToken = token.substring(0, token.length() - 2) + "xx";
        assertTrue(state.verify(tamperedToken).isEmpty());
    }

    @Test
    public void rejectsGarbageInput() {
        assertTrue(state.verify(null).isEmpty());
        assertTrue(state.verify("").isEmpty());
        assertTrue(state.verify("not-base64-!!!").isEmpty());
        assertTrue(state.verify(java.util.Base64.getUrlEncoder().encodeToString("garbage".getBytes())).isEmpty());
    }

    @Test
    public void rejectsExpiredState() throws Exception {
        // Build a state as if it were signed 20 minutes ago (MAX_AGE_SECONDS is 15 min).
        long staleTimestamp = Instant.now().getEpochSecond() - (20 * 60);
        String payload = "42.7." + staleTimestamp;

        java.lang.reflect.Method hmac = JiraOAuthState.class.getDeclaredMethod("hmac", String.class);
        hmac.setAccessible(true);
        String signature = (String) hmac.invoke(state, payload);

        String token = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "." + signature).getBytes());

        assertTrue(state.verify(token).isEmpty());
    }
}
