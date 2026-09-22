package com.app.repository;

import com.app.model.Nudge;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NudgeRepository extends JpaRepository<Nudge, UUID> {

  Optional<Nudge> findByIdAndCustomerId(UUID id, UUID customerId);

  List<Nudge> findByCustomerIdAndDismissedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
      UUID customerId, Instant now);

  /** Cooldown: has this trigger already fired for this customer inside the window? */
  boolean existsByCustomerIdAndTriggerCodeAndCreatedAtAfter(
      UUID customerId, String triggerCode, Instant after);

  long countByCustomerIdAndCreatedAtAfter(UUID customerId, Instant after);
}
