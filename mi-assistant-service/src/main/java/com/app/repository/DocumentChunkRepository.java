package com.app.repository;

import com.app.model.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

  List<DocumentChunk> findByDocId(UUID docId);

  @Modifying
  @Query("delete from DocumentChunk c where c.docId = :docId")
  int deleteByDocId(@Param("docId") UUID docId);

  /** Doc ids that already have at least one chunk, so reconciliation can skip them. */
  @Query("select distinct c.docId from DocumentChunk c")
  List<UUID> findIndexedDocIds();

  long countByCollectionCode(String collectionCode);
}
