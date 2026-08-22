package com.devpulse.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GithubApiClientTest {

    private RestTemplate restTemplate;
    private GithubApiClient githubApiClient;

    @BeforeEach
    public void setUp() {
        restTemplate = mock(RestTemplate.class);
        githubApiClient = new GithubApiClient(restTemplate, new ObjectMapper(), "https://api.github.com", "test-token");
    }

    @Test
    public void testFetchRepositoryDetailsReturnsParsedJson() {
        String repoJson = "{\"id\": 123, \"name\": \"Hello-World\", \"full_name\": \"octocat/Hello-World\"}";
        when(restTemplate.exchange(eq("https://api.github.com/repos/octocat/Hello-World"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(repoJson));

        JsonNode result = githubApiClient.fetchRepositoryDetails("octocat", "Hello-World");
        assertNotNull(result);
        assertEquals("Hello-World", result.path("name").asText());
    }

    @Test
    public void testFetchPullRequestsReturnsParsedJson() {
        String prsJson = "[{\"id\": 101, \"title\": \"Fix bug\"}]";
        when(restTemplate.exchange(contains("/pulls"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(prsJson));

        JsonNode result = githubApiClient.fetchPullRequests("octocat", "Hello-World", "all");
        assertNotNull(result);
        assertTrue(result.isArray());
        assertEquals("Fix bug", result.get(0).path("title").asText());
    }

    @Test
    public void testFetchCommitsReturnsParsedJson() {
        String commitsJson = "[{\"sha\": \"abc1234\", \"commit\": {\"message\": \"Initial commit\"}}]";
        when(restTemplate.exchange(contains("/commits"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(commitsJson));

        JsonNode result = githubApiClient.fetchCommits("octocat", "Hello-World");
        assertNotNull(result);
        assertTrue(result.isArray());
        assertEquals("abc1234", result.get(0).path("sha").asText());
    }
}
