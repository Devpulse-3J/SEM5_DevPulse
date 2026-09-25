package com.devpulse.metrics.controller;

import com.devpulse.metrics.dto.RelinkResponse;
import com.devpulse.metrics.security.RequestContext;
import com.devpulse.metrics.security.RequestContextResolver;
import com.devpulse.metrics.service.AuthorRelinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /metrics/authors/relink} - attribute the caller's earlier PRs to them. */
@RestController
@RequestMapping("/metrics/authors")
public class AuthorRelinkController {

    private final RequestContextResolver contextResolver;
    private final AuthorRelinkService relinkService;

    public AuthorRelinkController(RequestContextResolver contextResolver, AuthorRelinkService relinkService) {
        this.contextResolver = contextResolver;
        this.relinkService = relinkService;
    }

    @PostMapping("/relink")
    public RelinkResponse relink(HttpServletRequest request) {
        RequestContext context = contextResolver.resolve(request);
        return relinkService.relinkMyPullRequests(context);
    }
}
