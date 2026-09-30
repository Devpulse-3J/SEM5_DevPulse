package com.devpulse.integration.github;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

public class GithubAppClientTest {

    private static KeyPair keyPair;
    private static String pkcs8Pem;
    private static String pkcs1Pem;

    @BeforeAll
    public static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        byte[] pkcs8 = keyPair.getPrivate().getEncoded();
        // For a 2048-bit key the PKCS#8 wrapper is a fixed 26-byte header ending in
        // OCTET STRING (0x04) with a two-byte length; what follows is the PKCS#1 key.
        assertEquals(0x04, pkcs8[22]);
        byte[] pkcs1 = Arrays.copyOfRange(pkcs8, 26, pkcs8.length);

        pkcs8Pem = pem("PRIVATE KEY", pkcs8);
        pkcs1Pem = pem("RSA PRIVATE KEY", pkcs1);
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    private static GithubAppClient client(String appId, String pem) {
        String base64 = pem == null ? "" : Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
        return new GithubAppClient(mock(RestTemplate.class), new ObjectMapper(), "https://api.github.com", appId, base64);
    }

    @Test
    public void parsesThePkcs1FormatGithubIssues() {
        PrivateKey parsed = GithubAppClient.parsePrivateKey(pkcs1Pem);
        assertArrayEquals(keyPair.getPrivate().getEncoded(), parsed.getEncoded());
    }

    @Test
    public void parsesPkcs8Too() {
        PrivateKey parsed = GithubAppClient.parsePrivateKey(pkcs8Pem);
        assertArrayEquals(keyPair.getPrivate().getEncoded(), parsed.getEncoded());
    }

    @Test
    public void isNotConfiguredWithoutAKeyOrAppId() {
        assertFalse(client("5137471", null).isConfigured());
        assertFalse(client("", pkcs1Pem).isConfigured());
        assertTrue(client("5137471", pkcs1Pem).isConfigured());
    }

    @Test
    public void signsAJwtGithubWillAccept() throws Exception {
        String jwt = client("5137471", pkcs1Pem).createJwt();
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])));

        JsonNode header = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertEquals("RS256", header.path("alg").asText());
        assertEquals(5137471, claims.path("iss").asLong());
        // GitHub rejects App JWTs that live longer than 10 minutes.
        assertTrue(claims.path("exp").asLong() - claims.path("iat").asLong() <= 600);
    }

    @Test
    public void refusesToSignWhenUnconfigured() {
        assertThrows(IllegalStateException.class, () -> client("5137471", null).createJwt());
    }
}
