package com.app.repository;

import com.app.model.MemoryRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryRecordRepository extends JpaRepository<MemoryRecord, UUID> {

  /** Anti-IDOR: a record is only ever reachable through its own customer's namespace. */
  Optional<MemoryRecord> findByIdAndPseudoId(UUID id, String pseudoId);

  List<MemoryRecord> findByPseudoId(String pseudoId);

  /**
   * Optional filters use {@code coalesce} rather than {@code :param is null}: a bare null bind
   * has no type for Postgres to infer, which fails the whole statement.
   */
  @Query(
      """
      select r from MemoryRecord r
      where r.pseudoId = :pseudoId
        and r.supersedes is null
        and (r.validUntil is null or r.validUntil > :now)
        and r.kind = coalesce(:kind, r.kind)
      order by r.pinned desc, r.createdAt desc
      """)
  Page<MemoryRecord> findLive(
      @Param("pseudoId") String pseudoId,
      @Param("kind") String kind,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      """
      select r from MemoryRecord r
      where r.pseudoId = :pseudoId and r.pinned = true and r.supersedes is null
        and (r.validUntil is null or r.validUntil > :now)
        and r.state = 'CONFIRMED'
      order by r.createdAt desc
      """)
  List<MemoryRecord> findPinned(@Param("pseudoId") String pseudoId, @Param("now") Instant now);

  @Query(
      """
      select r from MemoryRecord r
      where r.pseudoId = :pseudoId and r.supersedes is null
        and r.state in ('CONFIRMED','HYPOTHESIS','INACTIVE','EXPIRED')
        and (r.validUntil is null or r.validUntil > :now)
      order by r.state, r.createdAt desc
      """)
  List<MemoryRecord> findGraphNodes(
      @Param("pseudoId") String pseudoId, @Param("now") Instant now);

  @Query(
      """
      select r from MemoryRecord r
      where r.pseudoId = :pseudoId and r.supersedes is null
        and r.state = 'HYPOTHESIS'
        and (r.suppressedUntil is null or r.suppressedUntil < :now)
      order by r.createdAt asc
      """)
  List<MemoryRecord> findHypotheses(
      @Param("pseudoId") String pseudoId, @Param("now") Instant now);

  @Query(
      """
      select r from MemoryRecord r
      where r.pseudoId = :pseudoId and r.supersedes is null
        and r.state = 'CONFIRMED' and r.entity = :entity
      order by r.createdAt desc
      """)
  List<MemoryRecord> findConfirmedByEntity(
      @Param("pseudoId") String pseudoId, @Param("entity") String entity);

  Optional<MemoryRecord> findByPseudoIdAndKindAndNormalisedKeyAndSupersedesIsNull(
      String pseudoId, String kind, String normalisedKey);

  long countByPseudoIdAndSourceAndCreatedAtAfter(String pseudoId, String source, Instant after);

  @Query(
      "select count(r) from MemoryRecord r where r.pseudoId = :pseudoId and r.supersedes is null")
  long countLive(@Param("pseudoId") String pseudoId);

  @Modifying
  @Query("delete from MemoryRecord r where r.pseudoId = :pseudoId")
  int deleteByPseudoId(@Param("pseudoId") String pseudoId);

  @Query(
      "select r from MemoryRecord r where r.validUntil is not null and r.validUntil < :now")
  List<MemoryRecord> findExpired(@Param("now") Instant now, Pageable pageable);
}
