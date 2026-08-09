package com.devpulse.auth.repository;

import com.devpulse.auth.entity.ProjectMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for the {@code project_members} table.
 * <p>
 * Used to resolve the per-project role for a given user — the
 * {@code (user, project) → role} lookup that the README defines.
 */
@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Integer> {

    /**
     * Returns all project memberships for a user (across all projects).
     */
    List<ProjectMember> findByUserId(Integer userId);

    /**
     * Resolves a user's role on a specific project.
     */
    Optional<ProjectMember> findByProjectIdAndUserId(Integer projectId, Integer userId);
}
