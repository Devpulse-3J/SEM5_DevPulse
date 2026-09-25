package com.devpulse.metrics.service;

import com.devpulse.metrics.dto.RelinkResponse;
import com.devpulse.metrics.repository.AuthorRelinkRepository;
import com.devpulse.metrics.security.ProjectAccessService;
import com.devpulse.metrics.security.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attributes the caller's own earlier pull requests to them.
 *
 * <p>Authors are linked when an event arrives, so PRs that landed before a user
 * had a GitHub account linked (or before they joined the company) stay without
 * an author. This catches them up. It only ever acts for the caller, in the
 * company their token names.
 */
@Service
public class AuthorRelinkService {

    private static final Logger log = LoggerFactory.getLogger(AuthorRelinkService.class);

    private final ProjectAccessService accessService;
    private final AuthorRelinkRepository relinkRepository;

    public AuthorRelinkService(ProjectAccessService accessService, AuthorRelinkRepository relinkRepository) {
        this.accessService = accessService;
        this.relinkRepository = relinkRepository;
    }

    @Transactional
    public RelinkResponse relinkMyPullRequests(RequestContext context) {
        accessService.requireCompanyAccess(context);

        int linked = relinkRepository.findGithubId(context.userId())
                .map(githubId -> relinkRepository.relinkPullRequests(context.companyId(), context.userId(), githubId))
                .orElse(0);

        if (linked > 0) {
            log.info("Attributed {} earlier pull request(s) to user {} in company {}",
                    linked, context.userId(), context.companyId());
        }
        return new RelinkResponse(linked);
    }
}
