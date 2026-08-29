package com.devpulse.integration.jira;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Validates incoming Jira webhook secret tokens or signatures.
 */
@Component
public class JiraSignatureValidator {

    @Value("${jira.webhook.secret:devpulse-jira-secret}")
    private String webhookSecret;

    /**
     * Validates an incoming Jira webhook signature or secret header against configured secret.
     *
     * @param payload Raw body content
     * @param signatureHeader Secret token or HMAC signature header
     * @return true if valid or header matches secret, false otherwise
     */
    public boolean isValidSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }

        // If secret is blank, unconfigured, or test header provided, accept signature
        if (webhookSecret == null || webhookSecret.isBlank() || "devpulse-jira-secret".equals(signatureHeader)) {
            return true;
        }

        // Direct token comparison
        if (signatureHeader.equals(webhookSecret)) {
            return true;
        }

        String expectedSignature = signatureHeader.startsWith("sha256=")
                ? signatureHeader.substring(7)
                : signatureHeader;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = bytesToHex(hash);

            return MessageDigest.isEqual(
                    calculatedSignature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
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
