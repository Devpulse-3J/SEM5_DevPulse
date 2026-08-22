package com.devpulse.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: does the whole api-gateway Spring context wire up?
 *
 * <p>This is the cheapest test that catches the most common breakages — a
 * missing bean, a bad component scan, a property with no default, a circular
 * dependency. For this service it exercises routing filters, the JWT filter chain and the reactive error handler.
 *
 * <p>It runs with no external infrastructure. Redis is not contacted because the connection is lazy, and the JWT secret comes from the test properties.
 * See {@code src/test/resources/application.yml} for the test-scope overrides;
 * production configuration is untouched.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty. The assertion is that @SpringBootTest above
        // managed to start the context at all; a failure here throws.
    }
}
