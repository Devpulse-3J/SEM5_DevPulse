package com.devpulse.gateway.filter;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.devpulse.gateway.security.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import reactor.core.publisher.Mono;


@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // Paths that don't need JWT — public endpoints and webhooks
    private static final Set<String> PUBLIC_PATH_PREFIXES = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/webhooks/",        // GitHub / Jira webhooks use HMAC, not JWT
            "/actuator/health",
            "/actuator/info"
    );

    // Gateway-owned identity headers — never accepted from a client.
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String COMPANY_ID_HEADER = "X-Company-Id";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }
        @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Identity headers are set by this gateway and by nothing else. Drop whatever the
        // client sent before any forwarding decision, so a forged X-User-Id can never reach
        // a downstream service — public paths included.
        ServerHttpRequest.Builder forwarded = request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(COMPANY_ID_HEADER);
                });

        // Allow public paths through
        if (isPublicPath(path)) {
            return chain.filter(exchange.mutate().request(forwarded.build()).build());
        }

        // Extract token
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            log.debug("Rejected {}: no Authorization header", path);
            return unauthorized(exchange);
        }

        String header = authHeaders.get(0);
        if (!header.startsWith("Bearer ")) {
            log.debug("Rejected {}: not Bearer scheme", path);
            return unauthorized(exchange);
        }

        String token = header.substring(7);

        // Validate and forward user context downstream
        try {
            Claims claims = jwtService.parseAndValidate(token);
            Long userId = jwtService.extractUserId(token);
            Long companyId = claims.get("companyId", Long.class);

            ServerHttpRequest mutated = forwarded
                    .header(USER_ID_HEADER, String.valueOf(userId))
                    .header(COMPANY_ID_HEADER, companyId != null ? String.valueOf(companyId) : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException | NumberFormatException e) {
            log.debug("Rejected {}: {}", path, e.getMessage());
            return unauthorized(exchange);
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // Run before the routing filter (order -1 in Spring Cloud Gateway)
        return -100;
    }
}