package com.devpulse.auth.repository;

import com.devpulse.auth.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for the {@code companies} table.
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Integer> {
}
