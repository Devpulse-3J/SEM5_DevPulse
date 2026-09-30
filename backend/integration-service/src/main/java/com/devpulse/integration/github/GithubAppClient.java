package com.devpulse.integration.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Talks to GitHub as the GitHub App itself: signs an App JWT with the App's
 * private key, exchanges it for per-installation access tokens, and lists the
 * repositories an installation was granted.
 *
 * <p>Only needs the App ID and private key. The App's client id/secret are not
 * used here — those env var names belong to auth-service's separate OAuth App
 * for sign-in, and sharing them is how the two got mixed up before.
 */
@Component
public class GithubAppClient {

    private static final Logger log = LoggerFactory.getLogger(GithubAppClient.class);
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(5);
    private static final int MAX_REPO_PAGES = 10;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String appId;
    private final PrivateKey privateKey;
    private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public record Installation(long id, String accountLogin, String accountType) {
    }

    public record InstallationRepo(long id, String name, String fullName, String htmlUrl, boolean privateRepo) {
    }

    private record CachedToken(String token, Instant expiresAt) {
    }

    public static class GithubAppException extends RuntimeException {
        public GithubAppException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public GithubAppClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${github.api.baseUrl:https://api.github.com}") String baseUrl,
            @Value("${github.app.id:}") String appId,
            @Value("${github.app.private-key-base64:}") String privateKeyBase64) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.appId = appId == null ? "" : appId.trim();
        this.privateKey = loadPrivateKey(privateKeyBase64);
    }

    public boolean isConfigured() {
        return !appId.isEmpty() && privateKey != null;
    }

    /** The installation as GitHub describes it, or empty if it is not an installation of this App. */
    public Optional<Installation> findInstallation(long installationId) {
        try {
            JsonNode node = send(HttpMethod.GET, baseUrl + "/app/installations/" + installationId, "Bearer " + createJwt());
            JsonNode account = node.path("account");
            return Optional.of(new Installation(
                    node.path("id").asLong(installationId),
                    account.path("login").asText(null),
                    account.path("type").asText(null)));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    /** A cached installation access token, refreshed shortly before GitHub expires it (after one hour). */
    public String installationToken(long installationId) {
        CachedToken cached = tokenCache.get(installationId);
        if (cached != null && Instant.now().isBefore(cached.expiresAt().minus(TOKEN_REFRESH_MARGIN))) {
            return cached.token();
        }
        JsonNode node = send(HttpMethod.POST,
                baseUrl + "/app/installations/" + installationId + "/access_tokens", "Bearer " + createJwt());
        String token = node.path("token").asText(null);
        if (token == null || token.isBlank()) {
            throw new GithubAppException("GitHub returned no installation token for " + installationId, null);
        }
        Instant expiresAt = Instant.parse(node.path("expires_at").asText(Instant.now().plus(Duration.ofHours(1)).toString()));
        tokenCache.put(installationId, new CachedToken(token, expiresAt));
        return token;
    }

    /** Every repository the installation was granted, across pages. */
    public List<InstallationRepo> listRepositories(long installationId) {
        String authorization = "Bearer " + installationToken(installationId);
        List<InstallationRepo> repos = new ArrayList<>();
        for (int page = 1; page <= MAX_REPO_PAGES; page++) {
            JsonNode node = send(HttpMethod.GET,
                    baseUrl + "/installation/repositories?per_page=100&page=" + page, authorization);
            JsonNode batch = node.path("repositories");
            for (JsonNode repo : batch) {
                repos.add(new InstallationRepo(
                        repo.path("id").asLong(0L),
                        repo.path("name").asText(""),
                        repo.path("full_name").asText(""),
                        repo.path("html_url").asText(""),
                        repo.path("private").asBoolean(false)));
            }
            if (batch.size() < 100 || repos.size() >= node.path("total_count").asInt(0)) {
                break;
            }
        }
        return repos;
    }

    private JsonNode send(HttpMethod method, String url, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("User-Agent", "DevPulse-Integration-Service");
        headers.set("Authorization", authorization);
        ResponseEntity<String> response = restTemplate.exchange(url, method, new HttpEntity<>(headers), String.class);
        try {
            return objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (Exception e) {
            throw new GithubAppException("Unreadable response from " + url, e);
        }
    }

    /** RS256 JWT identifying the App. GitHub accepts at most 10 minutes; iat is backdated for clock skew. */
    String createJwt() {
        if (!isConfigured()) {
            throw new IllegalStateException("GitHub App is not configured (GITHUB_APP_ID / GITHUB_APP_PRIVATE_KEY_BASE64)");
        }
        long now = Instant.now().getEpochSecond();
        String issuer = appId.matches("\\d+") ? appId : "\"" + appId + "\"";
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(String.format("{\"iat\":%d,\"exp\":%d,\"iss\":%s}", now - 60, now + 540, issuer)
                .getBytes(StandardCharsets.UTF_8));
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
            return header + "." + payload + "." + base64Url(signature.sign());
        } catch (Exception e) {
            throw new GithubAppException("Failed to sign GitHub App JWT", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Null (App unconfigured) when blank; a malformed key fails startup rather than every request later. */
    private static PrivateKey loadPrivateKey(String privateKeyBase64) {
        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
            log.warn("GitHub App private key not configured; installation-based repository access is disabled");
            return null;
        }
        String pem = new String(Base64.getDecoder().decode(privateKeyBase64.trim()), StandardCharsets.UTF_8);
        return parsePrivateKey(pem);
    }

    /** Accepts both PKCS#1 ("BEGIN RSA PRIVATE KEY", what GitHub issues) and PKCS#8 ("BEGIN PRIVATE KEY"). */
    static PrivateKey parsePrivateKey(String pem) {
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String body = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs1 ? wrapPkcs1(der) : der));
        } catch (Exception e) {
            throw new IllegalStateException("GitHub App private key could not be parsed", e);
        }
    }

    /** PrivateKeyInfo ::= SEQUENCE { INTEGER 0, SEQUENCE { rsaEncryption OID, NULL }, OCTET STRING pkcs1 } */
    private static byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] algorithm = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7,
                0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.writeBytes(version);
        content.writeBytes(algorithm);
        content.writeBytes(derTlv(0x04, pkcs1));
        return derTlv(0x30, content.toByteArray());
    }

    private static byte[] derTlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = value.length;
        if (length < 0x80) {
            out.write(length);
        } else {
            int byteCount = (Integer.SIZE - Integer.numberOfLeadingZeros(length) + 7) / 8;
            out.write(0x80 | byteCount);
            for (int i = byteCount - 1; i >= 0; i--) {
                out.write((length >> (8 * i)) & 0xff);
            }
        }
        out.writeBytes(value);
        return out.toByteArray();
    }
}
