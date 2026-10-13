package com.smartrecruit.user.repository;

import com.smartrecruit.user.model.DeletedCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeletedCandidateRepository extends JpaRepository<DeletedCandidate, UUID> {
}
