package com.devpulse.metrics.repository;

import com.devpulse.metrics.entity.PullRequestEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PullRequestRepository extends JpaRepository<PullRequestEntity, Integer> {
    Optional<PullRequestEntity> findByCompanyIdAndRepoIdAndGithubPrId(
            Integer companyId, Integer repoId, Long githubPrId);

    Optional<PullRequestEntity> findByCompanyIdAndRepoIdAndGithubPrNumber(
            Integer companyId, Integer repoId, Integer githubPrNumber);
}
