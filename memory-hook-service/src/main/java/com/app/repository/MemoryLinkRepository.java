package com.app.repository;

import com.app.model.MemoryLink;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryLinkRepository extends JpaRepository<MemoryLink, UUID> {

  @Query(
      """
      select l from MemoryLink l
      where l.pseudoId = :pseudoId
        and l.state in ('HYPOTHESIS','CONFIRMED')
        and l.fromRecord in :recordIds and l.toRecord in :recordIds
      order by l.createdAt desc
      """)
  List<MemoryLink> findGraphLinks(
      @Param("pseudoId") String pseudoId, @Param("recordIds") List<UUID> recordIds);

  @Query(
      """
      select l from MemoryLink l
      where l.pseudoId = :pseudoId and l.state = 'HYPOTHESIS'
      order by l.createdAt asc
      """)
  List<MemoryLink> findHypothesisLinks(@Param("pseudoId") String pseudoId);
}
