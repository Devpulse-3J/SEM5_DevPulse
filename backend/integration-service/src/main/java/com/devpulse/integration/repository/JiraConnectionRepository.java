package com.devpulse.integration.repository;

import com.devpulse.integration.entity.JiraConnection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JiraConnectionRepository extends JpaRepository<JiraConnection, Integer> {
    Optional<JiraConnection> findByCompanyIdAndActiveTrue(Integer companyId);
    Optional<JiraConnection> findByCompanyId(Integer companyId);
}
