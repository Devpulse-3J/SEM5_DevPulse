package com.devpulse.integration.security;

import com.devpulse.integration.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Reads the caller's identity from the gateway-set {@code X-User-Id} and
 * {@code X-Company-Id} headers.
 *
 * <p>integration-service has no Spring Security on the classpath, so these
 * headers are the only identity available. They are trustworthy because
 * api-gateway strips whatever the client sent before re-adding them from
 * validated JWT claims — which also means this service must never be reachable
 * except through the gateway.
 *
 * <p>Note this applies to {@code /integrations/**} only. The webhook endpoints
 * are a public path at the gateway and authenticate by HMAC instead; they never
 * see these headers.
 *
 * <p>Mirrors the resolver of the same name in metrics-service.
 */
@Component
public class RequestContextResolver {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String COMPANY_ID_HEADER = "X-Company-Id";

    public RequestContext resolve(HttpServletRequest request) {
        String userHeader = request.getHeader(USER_ID_HEADER);
        String companyHeader = request.getHeader(COMPANY_ID_HEADER);

        if (isBlank(userHeader) || isBlank(companyHeader)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Gateway identity headers are missing");
        }

        try {
            int userId = Integer.parseInt(userHeader.trim());
            int companyId = Integer.parseInt(companyHeader.trim());
            if (userId < 1 || companyId < 1) {
                throw new NumberFormatException("IDs must be positive");
            }
            return new RequestContext(userId, companyId);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Gateway identity headers are invalid");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
