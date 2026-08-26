package com.devpulse.auth.security;

import com.devpulse.auth.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Reads the caller's identity from the gateway-set {@code X-User-Id} and
 * {@code X-Company-Id} headers.
 *
 * <p>These headers are trustworthy only because
 * {@code api-gateway}'s {@code JwtAuthenticationFilter} strips whatever the
 * client sent before re-adding them from validated JWT claims — on every path,
 * public ones included. A request that reaches this service without passing
 * through the gateway can assert any identity it likes, so <b>auth-service must
 * never be exposed directly</b>; only port 8080 is public.
 *
 * <p>Spring Security still authenticates the Bearer token independently
 * ({@code anyRequest().authenticated()} in {@code SecurityConfig}), so a
 * request needs both a valid JWT and these headers to reach a controller. The
 * two are cross-checked in {@link ProjectAccessService#requireCaller}.
 *
 * <p>Mirrors {@code metrics-service}'s resolver of the same name so both
 * services read identity identically.
 */
@Component
public class RequestContextResolver {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String COMPANY_ID_HEADER = "X-Company-Id";

    public RequestContext resolve(HttpServletRequest request) {
        String userHeader = request.getHeader(USER_ID_HEADER);
        String companyHeader = request.getHeader(COMPANY_ID_HEADER);

        if (isBlank(userHeader) || isBlank(companyHeader)) {
            throw new UnauthorizedException("Gateway identity headers are missing");
        }

        try {
            int userId = Integer.parseInt(userHeader.trim());
            int companyId = Integer.parseInt(companyHeader.trim());
            if (userId < 1 || companyId < 1) {
                throw new NumberFormatException("IDs must be positive");
            }
            return new RequestContext(userId, companyId);
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException("Gateway identity headers are invalid");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
