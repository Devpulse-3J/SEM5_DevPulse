package com.devpulse.auth.repository;

import com.devpulse.auth.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for the {@code companies} table.
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Integer> {
    List<Company> findByCompanyNameContainingIgnoreCase(String companyName);
}
