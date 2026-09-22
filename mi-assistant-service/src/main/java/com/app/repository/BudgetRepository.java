package com.app.repository;

import com.app.model.Budget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

  Optional<Budget> findByCustomerIdAndCategoryCodeAndActiveTrue(UUID customerId, String category);

  List<Budget> findByCustomerIdAndActiveTrue(UUID customerId);
}
