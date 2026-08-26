package com.devpulse.auth.repository;

import com.devpulse.auth.entity.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Integer> {

    List<Project> findByCompanyCompanyId(Integer companyId);

    Optional<Project> findByProjectIdAndCompanyCompanyId(Integer projectId, Integer companyId);

    boolean existsByCompanyCompanyIdAndProjectNameIgnoreCase(Integer companyId, String projectName);
}
