package com.app.service.rag;

import java.util.List;
import java.util.UUID;

/** The pgvector side of {@code document_chunk}, reached with native SQL. */
public interface ChunkVectorStore {

  void upsert(UUID chunkId, float[] embedding);

  /** Cosine similarity restricted to the collections the active agent may read. */
  List<Hit> search(List<String> collectionCodes, String locale, float[] query, int topK);

  record Hit(UUID chunkId, double score) {}
}
