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
}
