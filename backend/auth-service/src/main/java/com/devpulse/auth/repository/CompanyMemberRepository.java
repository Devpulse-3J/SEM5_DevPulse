package com.devpulse.auth.repository;

import com.devpulse.auth.entity.CompanyMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for the {@code company_members} table.
 */
@Repository
public interface CompanyMemberRepository extends JpaRepository<CompanyMember, Integer> {

    Optional<CompanyMember> findByUserIdAndCompanyId(Integer userId, Integer companyId);

    List<CompanyMember> findByUserId(Integer userId);

    List<CompanyMember> findByCompanyId(Integer companyId);

    boolean existsByUserIdAndRole(Integer userId, String role);
}
