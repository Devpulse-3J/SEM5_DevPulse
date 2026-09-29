package com.devpulse.notification.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class AlertAccessService {

    private final JdbcTemplate jdbcTemplate;

    public AlertAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireManagerOrAdmin(Integer companyId, Integer userId) {
        if (companyId == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing identity context");
        }

        // 1. Check if user is system/company admin
        Optional<String> systemRole = findSystemRole(companyId, userId);
        if (systemRole.isPresent() && "admin".equalsIgnoreCase(systemRole.get())) {
            return;
        }

        // 2. Check if user is a manager in project_members for this company
        if (isManagerInCompany(companyId, userId)) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only managers and administrators can view or configure alert rules");
    }

    private Optional<String> findSystemRole(Integer companyId, Integer userId) {
        List<String> roles = jdbcTemplate.query("""
                SELECT role FROM (
                    SELECT role, 0 AS priority FROM company_members WHERE company_id = ? AND user_id = ?
                    UNION ALL
                    SELECT system_role AS role, 1 AS priority FROM users WHERE company_id = ? AND user_id = ?
                ) r ORDER BY priority LIMIT 1
                """, (rs, rowNum) -> rs.getString("role"), companyId, userId, companyId, userId);
        return roles.stream().findFirst();
    }

    private boolean isManagerInCompany(Integer companyId, Integer userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM project_members pm
                JOIN projects p ON pm.project_id = p.project_id
                WHERE p.company_id = ? AND pm.user_id = ? AND LOWER(pm.role) = 'manager'
                """, Integer.class, companyId, userId);
        return count != null && count > 0;
    }
}