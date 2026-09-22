package com.app.repository;

import com.app.model.AccessAudit;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessAuditRepository extends JpaRepository<AccessAudit, UUID> {

  Page<AccessAudit> findByPseudoIdAndAtBetweenOrderByAtDesc(
      String pseudoId, Instant from, Instant to, Pageable pageable);

  Page<AccessAudit> findByAtBetweenOrderByAtDesc(Instant from, Instant to, Pageable pageable);

  long countByPseudoIdAndOperationAndAtAfter(String pseudoId, String operation, Instant after);

  @Modifying
  @Query("delete from AccessAudit a where a.pseudoId = :pseudoId")
  int deleteByPseudoId(@Param("pseudoId") String pseudoId);

  @Modifying
  @Query("delete from AccessAudit a where a.at < :before")
  int deleteOlderThan(@Param("before") Instant before);
}
