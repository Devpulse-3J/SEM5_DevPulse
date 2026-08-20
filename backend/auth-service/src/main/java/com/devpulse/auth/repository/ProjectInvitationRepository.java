package com.devpulse.auth.repository;

import com.devpulse.auth.entity.ProjectInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Integer> {

    List<ProjectInvitation> findByProjectIdAndStatus(Integer projectId, String status);

    Optional<ProjectInvitation> findByProjectIdAndUserUserId(Integer projectId, Integer userId);
}
