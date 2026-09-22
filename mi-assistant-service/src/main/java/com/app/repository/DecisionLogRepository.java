package com.app.repository;

import com.app.model.DecisionLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionLogRepository extends JpaRepository<DecisionLog, UUID> {

  Optional<DecisionLog> findByIdAndCustomerId(UUID id, UUID customerId);
}
