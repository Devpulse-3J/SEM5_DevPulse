package com.devpulse.metrics.repository;

import com.devpulse.metrics.entity.DeploymentEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, Integer> {
    Optional<DeploymentEntity> findByCompanyIdAndGithubDeploymentId(
            Integer companyId, Long githubDeploymentId);
}
