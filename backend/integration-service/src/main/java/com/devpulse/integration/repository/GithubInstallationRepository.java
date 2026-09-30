package com.devpulse.integration.repository;

import com.devpulse.integration.entity.GithubInstallation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GithubInstallationRepository extends JpaRepository<GithubInstallation, Long> {
    List<GithubInstallation> findByCompanyId(Integer companyId);
}
