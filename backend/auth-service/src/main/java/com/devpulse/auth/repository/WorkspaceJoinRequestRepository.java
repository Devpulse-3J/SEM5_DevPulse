package com.devpulse.auth.repository;

import com.devpulse.auth.entity.WorkspaceJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceJoinRequestRepository extends JpaRepository<WorkspaceJoinRequest, Integer> {

    List<WorkspaceJoinRequest> findByCompanyCompanyIdAndStatus(Integer companyId, String status);

    Optional<WorkspaceJoinRequest> findByCompanyCompanyIdAndUserUserId(Integer companyId, Integer userId);

    boolean existsByCompanyCompanyIdAndUserUserIdAndStatus(Integer companyId, Integer userId, String status);
}
