package com.devpulse.metrics.repository;

import com.devpulse.metrics.entity.CommitEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitRepository extends JpaRepository<CommitEntity, String> {
    Optional<CommitEntity> findByCommitShaAndCompanyId(String commitSha, Integer companyId);
}
