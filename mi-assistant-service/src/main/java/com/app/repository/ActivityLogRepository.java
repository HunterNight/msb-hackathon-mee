package com.app.repository;

import com.app.model.ActivityLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

  Page<ActivityLog> findByCustomerIdAndKindInOrderByOccurredAtDesc(
      UUID customerId, List<String> kinds, Pageable pageable);
}
