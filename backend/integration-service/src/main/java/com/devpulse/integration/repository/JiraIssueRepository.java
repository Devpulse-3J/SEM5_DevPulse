package com.devpulse.integration.repository;

import com.devpulse.integration.entity.JiraIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JiraIssueRepository extends JpaRepository<JiraIssue, Integer> {
    Optional<JiraIssue> findByCompanyIdAndJiraKey(Integer companyId, String jiraKey);
    List<JiraIssue> findByCompanyId(Integer companyId);
    List<JiraIssue> findByProjectId(Integer projectId);
}
