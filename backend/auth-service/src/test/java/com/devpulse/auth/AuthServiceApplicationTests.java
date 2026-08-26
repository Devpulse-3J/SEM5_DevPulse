package com.devpulse.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: does the whole auth-service Spring context wire up?
 *
 * <p>This is the cheapest test that catches the most common breakages — a
 * missing bean, a bad component scan, a property with no default, a circular
 * dependency. For this service it exercises the security filter chain, JPA repositories and the JWT components.
 *
 * <p>It runs with no external infrastructure. Postgres is replaced by an in-memory H2 database and the RabbitMQ listener containers are not started.
 * See {@code src/test/resources/application.yml} for the test-scope overrides;
 * production configuration is untouched.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty. The assertion is that @SpringBootTest above
        // managed to start the context at all; a failure here throws.
    }
}
