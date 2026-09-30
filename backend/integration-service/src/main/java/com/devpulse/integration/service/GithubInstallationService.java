package com.devpulse.integration.service;

import com.devpulse.integration.entity.GithubInstallation;
import com.devpulse.integration.exception.ApiException;
import com.devpulse.integration.github.GithubAppClient;
import com.devpulse.integration.github.GithubAppClient.InstallationRepo;
import com.devpulse.integration.repository.GithubInstallationRepository;
import com.devpulse.integration.security.ProjectAccessService;
import com.devpulse.integration.security.RequestContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Binds GitHub App installations to companies and reads repositories through them.
 *
 * <p>The installation id arrives as a query parameter on GitHub's redirect back
 * to the admin page, so it is caller-supplied. Before storing it: the caller must
 * be an admin, GitHub must confirm it is an installation of this App, and it must
 * not already belong to another company (first claim wins).
 */
@Service
public class GithubInstallationService {

    private static final Logger log = LoggerFactory.getLogger(GithubInstallationService.class);

    private final GithubInstallationRepository installationRepository;
    private final GithubAppClient appClient;
    private final ProjectAccessService projectAccessService;

    public GithubInstallationService(GithubInstallationRepository installationRepository,
                                     GithubAppClient appClient,
                                     ProjectAccessService projectAccessService) {
        this.installationRepository = installationRepository;
        this.appClient = appClient;
        this.projectAccessService = projectAccessService;
    }

    @Transactional
    public GithubInstallation claim(RequestContext context, Integer projectId, long installationId) {
        projectAccessService.requireAdminOnProject(context, projectId);
        if (!appClient.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The GitHub App is not configured on the server (GITHUB_APP_ID / GITHUB_APP_PRIVATE_KEY_BASE64).");
        }

        GithubAppClient.Installation installation = appClient.findInstallation(installationId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Installation " + installationId + " is not an installation of this GitHub App."));

        Optional<GithubInstallation> existing = installationRepository.findById(installationId);
        if (existing.isPresent() && !existing.get().getCompanyId().equals(context.companyId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This GitHub App installation is already connected to another company.");
        }

        GithubInstallation row = existing.orElseGet(() -> new GithubInstallation(installationId, context.companyId()));
        row.setAccountLogin(installation.accountLogin());
        row.setAccountType(installation.accountType());
        row.setUpdatedAt(Instant.now());
        GithubInstallation saved = installationRepository.save(row);

        log.info("Admin user {} connected GitHub App installation {} ({}) to company {}",
                context.userId(), installationId, installation.accountLogin(), context.companyId());
        return saved;
    }

    public boolean hasInstallation(Integer companyId) {
        return appClient.isConfigured() && !installationRepository.findByCompanyId(companyId).isEmpty();
    }

    /** Repositories across all of the company's installations, deduplicated by full name. */
    public List<InstallationRepo> listRepositories(Integer companyId) {
        Map<String, InstallationRepo> repos = new LinkedHashMap<>();
        for (GithubInstallation installation : installationsFor(companyId)) {
            try {
                for (InstallationRepo repo : appClient.listRepositories(installation.getInstallationId())) {
                    repos.putIfAbsent(repo.fullName().toLowerCase(), repo);
                }
            } catch (Exception e) {
                // One uninstalled or revoked installation must not hide the others' repos.
                log.warn("Could not list repositories for installation {}: {}",
                        installation.getInstallationId(), e.getMessage());
            }
        }
        return new ArrayList<>(repos.values());
    }

    /** An installation token that can read the given repository, if any of the company's installations covers it. */
    public Optional<String> tokenForRepository(Integer companyId, String fullName) {
        for (GithubInstallation installation : installationsFor(companyId)) {
            try {
                boolean covers = appClient.listRepositories(installation.getInstallationId()).stream()
                        .anyMatch(repo -> repo.fullName().equalsIgnoreCase(fullName));
                if (covers) {
                    return Optional.of(appClient.installationToken(installation.getInstallationId()));
                }
            } catch (Exception e) {
                log.warn("Could not check installation {} for {}: {}",
                        installation.getInstallationId(), fullName, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private List<GithubInstallation> installationsFor(Integer companyId) {
        return appClient.isConfigured() ? installationRepository.findByCompanyId(companyId) : List.of();
    }
}
