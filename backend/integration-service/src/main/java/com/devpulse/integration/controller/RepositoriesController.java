package com.devpulse.integration.controller;

import com.devpulse.integration.dto.RepositoryDto;
import com.devpulse.integration.entity.Repo;
import com.devpulse.integration.exception.ApiException;
import com.devpulse.integration.repository.RepoRepository;
import com.devpulse.integration.security.RequestContext;
import com.devpulse.integration.security.RequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The repositories visible to the caller's company.
 *
 * <p>Mapped under {@code /integrations/**} rather than {@code /repositories/**}
 * so it falls inside the gateway's existing {@code /api/integrations/**} route —
 * no gateway change is needed. See {@link ProjectGithubController} for why a
 * top-level path would have needed one.
 *
 * <pre>
 *   GET /api/integrations/repositories        — all repos in the company
 *   GET /api/integrations/repositories/{id}   — one repo, company scoped
 * </pre>
 *
 * <p>Every query is filtered by the company id the gateway asserts; none is ever
 * by primary key alone.
 */
@RestController
@RequestMapping("/integrations/repositories")
public class RepositoriesController {

    private final RepoRepository repoRepository;
    private final RequestContextResolver contextResolver;

    public RepositoriesController(RepoRepository repoRepository,
                                  RequestContextResolver contextResolver) {
        this.repoRepository = repoRepository;
        this.contextResolver = contextResolver;
    }

    /** Every repository belonging to the caller's company. */
    @GetMapping
    public ResponseEntity<List<RepositoryDto>> list(HttpServletRequest servletRequest) {
        RequestContext context = contextResolver.resolve(servletRequest);

        List<RepositoryDto> repositories = repoRepository.findByCompanyId(context.companyId())
                .stream()
                .map(RepositoryDto::from)
                .toList();

        return ResponseEntity.ok(repositories);
    }

    /**
     * One repository by id. A repo belonging to another company is reported as
     * 404 rather than 403 — the caller learns nothing about whether the id
     * exists elsewhere.
     */
    @GetMapping("/{id}")
    public ResponseEntity<RepositoryDto> get(HttpServletRequest servletRequest,
                                             @PathVariable("id") Integer id) {
        RequestContext context = contextResolver.resolve(servletRequest);

        Repo repo = repoRepository.findByRepoIdAndCompanyId(id, context.companyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Repository not found: " + id));

        return ResponseEntity.ok(RepositoryDto.from(repo));
    }
}
