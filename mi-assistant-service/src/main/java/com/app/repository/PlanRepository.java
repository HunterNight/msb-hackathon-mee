package com.app.repository;

import com.app.model.Plan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

  Optional<Plan> findByIdAndCustomerId(UUID id, UUID customerId);

  List<Plan> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, String status);

  List<Plan> findByStatus(String status);
}
