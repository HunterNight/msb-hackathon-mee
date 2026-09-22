package com.app.repository;

import com.app.model.SafetyFlag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyFlagRepository extends JpaRepository<SafetyFlag, UUID> {

  long countByCustomerIdAndLabelAndAtAfter(UUID customerId, String label, Instant after);
}
