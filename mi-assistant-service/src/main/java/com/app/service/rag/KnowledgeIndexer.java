package com.app.service.rag;

import java.util.UUID;

/** Re-chunks and re-embeds a document when the CMS publishes or archives it (design §3.1). */
public interface KnowledgeIndexer {

  int index(UUID docId);

  void remove(UUID docId);

  /** Full re-embed of one collection, or of everything when {@code collectionCode} is null. */
  int reindex(String collectionCode);

  /**
   * Embeds published documents that have no chunks yet, leaving the ones already indexed alone.
   * Returns how many were newly indexed.
   */
  int indexMissing();
}
