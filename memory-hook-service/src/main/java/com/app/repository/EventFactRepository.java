package com.app.repository;

import com.app.model.EventFact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventFactRepository extends JpaRepository<EventFact, UUID> {

  boolean existsByEventIdAndFactTypeAndRefKey(String eventId, String factType, String refKey);

  List<EventFact> findByPseudoIdAndFactTypeAndRefKeyOrderByOccurredAtDesc(
      String pseudoId, String factType, String refKey);

  List<EventFact> findByPseudoIdAndFactTypeAndOccurredAtAfter(
      String pseudoId, String factType, Instant after);

  List<EventFact> findByPseudoIdAndOccurredAtAfter(String pseudoId, Instant after);

  @Modifying
  @Query("delete from EventFact f where f.pseudoId = :pseudoId")
  int deleteByPseudoId(@Param("pseudoId") String pseudoId);

  @Modifying
  @Query("delete from EventFact f where f.occurredAt < :before")
  int deleteOlderThan(@Param("before") Instant before);
}
