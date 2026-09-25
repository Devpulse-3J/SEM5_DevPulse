package com.devpulse.metrics.repository;

import com.devpulse.metrics.entity.PrReviewEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PrReviewRepository extends JpaRepository<PrReviewEntity, Integer> {

    List<PrReviewEntity> findByCompanyIdAndPrId(Integer companyId, Integer prId);

    List<PrReviewEntity> findByCompanyIdAndReviewerId(Integer companyId, Integer reviewerId);
}
