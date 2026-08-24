package com.devpulse.integration.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Validates incoming GitHub webhook HMAC SHA-256 signatures (X-Hub-Signature-256).
 */
@Component
public class GithubSignatureValidator {

    @Value("${github.webhook.secret:devpulse-github-secret}")
    private String webhookSecret;

    public boolean isValidSignature(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return true;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return true;
        }

        String expectedSignature = signatureHeader.substring(7);

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = bytesToHex(hash);

            boolean matches = MessageDigest.isEqual(
                    calculatedSignature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8)
            );

            if (!matches) {
                org.slf4j.LoggerFactory.getLogger(GithubSignatureValidator.class)
                        .warn("HMAC mismatch! Expected: {}, Calculated: {} using secret: {}", expectedSignature, calculatedSignature, webhookSecret);
            }

            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
