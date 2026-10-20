package com.smartrecruit.user.repository;

import com.smartrecruit.user.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    /** The company a recruiter belongs to (at most one in the MVP). */
    @Query("select c from Company c join c.members m where m = :accountId")
    Optional<Company> findByMember(@Param("accountId") UUID accountId);
}
