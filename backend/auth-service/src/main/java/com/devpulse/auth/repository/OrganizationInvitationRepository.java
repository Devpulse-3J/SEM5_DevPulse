package com.devpulse.auth.repository;

import com.devpulse.auth.entity.OrganizationInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, Integer> {

    Optional<OrganizationInvitation> findByToken(String token);

    List<OrganizationInvitation> findByCompanyCompanyIdAndStatus(Integer companyId, String status);

    boolean existsByCompanyCompanyIdAndEmailAndStatus(Integer companyId, String email, String status);
}
