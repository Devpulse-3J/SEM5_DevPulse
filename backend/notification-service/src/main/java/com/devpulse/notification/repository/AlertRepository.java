package com.devpulse.notification.repository;

import com.devpulse.notification.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Integer> {
    List<Alert> findByCompanyIdOrderByTriggeredAtDesc(Integer companyId);
    List<Alert> findByCompanyIdAndResolvedAtIsNullOrderByTriggeredAtDesc(Integer companyId);
}
