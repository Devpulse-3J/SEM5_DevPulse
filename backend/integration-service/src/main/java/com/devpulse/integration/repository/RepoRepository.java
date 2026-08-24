package com.devpulse.integration.repository;

import com.devpulse.integration.entity.Repo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepoRepository extends JpaRepository<Repo, Integer> {
    Optional<Repo> findByCompanyIdAndGithubRepoId(Integer companyId, Long githubRepoId);
    Optional<Repo> findByFullName(String fullName);
    List<Repo> findByCompanyId(Integer companyId);
    List<Repo> findByProjectId(Integer projectId);

    /**
     * The repositories linked to one project, constrained to a tenant.
     * {@code findByProjectId} alone would cross company boundaries if two
     * tenants ever shared a project id.
     */
    List<Repo> findByCompanyIdAndProjectId(Integer companyId, Integer projectId);

    /**
     * One repository by primary key, constrained to a tenant. Looking it up by
     * id alone would let a caller read another company's repo, so the detail
     * endpoint always pairs the id with the caller's company.
     */
    Optional<Repo> findByRepoIdAndCompanyId(Integer repoId, Integer companyId);
}
