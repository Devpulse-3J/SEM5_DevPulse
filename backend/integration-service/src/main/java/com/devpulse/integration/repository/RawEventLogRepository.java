package com.devpulse.integration.repository;

import com.devpulse.integration.entity.RawEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RawEventLogRepository extends JpaRepository<RawEventLog, Integer> {
    List<RawEventLog> findByProcessedAtIsNull();
    List<RawEventLog> findByCompanyId(Integer companyId);
}
