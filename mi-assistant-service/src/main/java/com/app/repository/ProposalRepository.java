package com.app.repository;

import com.app.model.Proposal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalRepository extends JpaRepository<Proposal, UUID> {

  Optional<Proposal> findByIdAndCustomerId(UUID id, UUID customerId);

  List<Proposal> findByPhaseAndExpiresAtBefore(String phase, Instant before, Pageable pageable);
}
