package com.app.repository;

import com.app.model.Insight;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsightRepository extends JpaRepository<Insight, UUID> {

  Optional<Insight> findFirstByCustomerIdAndPlacementAndValidUntilAfterOrderByCreatedAtDesc(
      UUID customerId, String placement, Instant now);

  List<Insight> findByCustomerIdAndValidUntilAfter(UUID customerId, Instant now);
}
