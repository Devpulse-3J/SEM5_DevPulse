package com.devpulse.integration.security;

/**
 * The caller's identity, as asserted by the API gateway.
 *
 * @param userId    from {@code X-User-Id}
 * @param companyId from {@code X-Company-Id}
 */
public record RequestContext(Integer userId, Integer companyId) {
}
