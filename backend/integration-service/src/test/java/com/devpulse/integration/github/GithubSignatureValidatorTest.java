package com.devpulse.integration.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class GithubSignatureValidatorTest {

    private GithubSignatureValidator validator;
    private final String secret = "test-secret";

    @BeforeEach
    public void setUp() {
        validator = new GithubSignatureValidator();
        ReflectionTestUtils.setField(validator, "webhookSecret", secret);
    }

    @Test
    public void testValidSignature() throws Exception {
        String payload = "{\"action\":\"opened\"}";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        String validHeader = "sha256=" + hexString.toString();

        assertTrue(validator.isValidSignature(payload, validHeader));
    }

    @Test
    public void testInvalidSignature() {
        String payload = "{\"action\":\"opened\"}";
        String invalidHeader = "sha256=invalidhash12345";

        assertFalse(validator.isValidSignature(payload, invalidHeader));
    }

    @Test
    public void testNullHeader() {
        assertFalse(validator.isValidSignature("payload", null));
    }
}
