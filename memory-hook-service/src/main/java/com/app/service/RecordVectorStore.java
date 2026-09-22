package com.app.service;

import java.util.List;
import java.util.UUID;

/**
 * The pgvector side of {@code memory_record}. Kept out of JPA on purpose: the vector column is
 * written and searched with native SQL so the entity stays free of a database-specific type.
 */
public interface RecordVectorStore {

  void upsert(UUID recordId, float[] embedding);

  /** Cosine similarity within one customer's namespace — never across namespaces. */
  List<Hit> search(String pseudoId, float[] query, int topK);

  int countFor(String pseudoId);

  /** Used by erasure to prove the vectors are gone. */
  int clearFor(String pseudoId);

  record Hit(UUID recordId, double score) {}
}
