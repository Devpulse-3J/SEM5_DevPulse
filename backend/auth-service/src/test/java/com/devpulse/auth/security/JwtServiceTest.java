package com.devpulse.auth.security;

import com.devpulse.auth.entity.Company;
import com.devpulse.auth.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code generateToken(User)} (login/register) must keep issuing a token
 * scoped to the user's home company, unchanged, now that it delegates to the
 * new {@code generateToken(User, companyId, role)} overload used by
 * {@code /auth/companies/{id}/switch}.
 */
public class JwtServiceTest {

    private static final String SECRET = "test-only-secret-key-min-32-chars-long-not-a-real-secret";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    public void setUp() {
        jwtService = new JwtService(SECRET, 3600);

        Company company = new Company();
        company.setCompanyId(6);

        user = new User();
        user.setUserId(20);
        user.setEmail("dev@example.com");
        user.setCompany(company);
        user.setSystemRole("member");
    }

    @Test
    public void generateTokenFromUserCarriesTheirHomeCompanyAndRole() {
        String token = jwtService.generateToken(user);

        Claims claims = jwtService.validateToken(token);
        assertEquals("20", claims.getSubject());
        assertEquals("dev@example.com", claims.get("email", String.class));
        assertEquals(6, claims.get("companyId", Integer.class));
        assertEquals("member", claims.get("systemRole", String.class));
    }

    @Test
    public void generateTokenFromUserWithNoCompanyLeavesCompanyIdNull() {
        user.setCompany(null);

        String token = jwtService.generateToken(user);

        Claims claims = jwtService.validateToken(token);
        assertNull(claims.get("companyId", Integer.class));
    }

    @Test
    public void generateTokenWithAnExplicitCompanyOverridesTheHomeCompanyAndRole() {
        // This is the switch-company path: a different company and role than
        // the user's own row in `users`.
        String token = jwtService.generateToken(user, 9, "admin");

        Claims claims = jwtService.validateToken(token);
        assertEquals("20", claims.getSubject());
        assertEquals(9, claims.get("companyId", Integer.class));
        assertEquals("admin", claims.get("systemRole", String.class));
    }

    @Test
    public void extractUserIdReadsTheSubjectClaim() {
        String token = jwtService.generateToken(user);

        assertEquals(20, jwtService.extractUserId(token));
    }
}
