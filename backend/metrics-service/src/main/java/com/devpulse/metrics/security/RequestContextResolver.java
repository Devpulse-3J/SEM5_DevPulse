package com.devpulse.metrics.security;

import com.devpulse.metrics.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RequestContextResolver {

    public RequestContext resolve(HttpServletRequest request) {
        String userHeader = firstPresent(request, "X-User-Id", "X-DevPulse-User-Id");
        String companyHeader = firstPresent(request, "X-Company-Id", "X-DevPulse-Company-Id");
        if (userHeader == null || companyHeader == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Gateway identity headers are missing");
        }
        try {
            int userId = Integer.parseInt(userHeader);
            int companyId = Integer.parseInt(companyHeader);
            if (userId < 1 || companyId < 1) {
                throw new NumberFormatException("IDs must be positive");
            }
            return new RequestContext(userId, companyId);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_IDENTITY_CONTEXT",
                    "Gateway identity headers are invalid");
        }
    }

    private String firstPresent(HttpServletRequest request, String primary, String compatibility) {
        String value = request.getHeader(primary);
        return value == null || value.isBlank() ? request.getHeader(compatibility) : value;
    }
}
