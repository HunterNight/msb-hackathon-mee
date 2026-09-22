package com.app.service.impl;

import com.app.constant.MiConstants;
import com.app.model.DocumentChunk;
import com.app.repository.DocumentChunkRepository;
import com.app.service.client.CmsClient;
import com.app.service.rag.ChunkVectorStore;
import com.app.service.rag.EmbeddingService;
import com.app.service.rag.KnowledgeIndexer;
import com.app.service.security.InputScreeningService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class KnowledgeIndexerImpl implements KnowledgeIndexer {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexerImpl.class);
  private static final int CHUNK_CHARS = MiConstants.RAG_CHUNK_TOKENS * MiConstants.CHARS_PER_TOKEN;
  private static final int OVERLAP_CHARS =
      MiConstants.RAG_CHUNK_OVERLAP * MiConstants.CHARS_PER_TOKEN;

  private final CmsClient cms;
  private final DocumentChunkRepository chunks;
  private final ChunkVectorStore vectors;
  private final EmbeddingService embeddings;
  private final InputScreeningService screening;
  private final ObjectMapper objectMapper;

  public KnowledgeIndexerImpl(
      CmsClient cms,
      DocumentChunkRepository chunks,
      ChunkVectorStore vectors,
      EmbeddingService embeddings,
      InputScreeningService screening,
      ObjectMapper objectMapper) {
    this.cms = cms;
    this.chunks = chunks;
    this.vectors = vectors;
    this.embeddings = embeddings;
    this.screening = screening;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public int index(UUID docId) {
    Optional<CmsClient.CmsDocument> document = cms.currentDoc(docId);
    if (document.isEmpty()) {
      return 0;
    }
    CmsClient.CmsDocument doc = document.get();
    // Replace rather than append: a new version must not leave the old passages retrievable.
    chunks.deleteByDocId(docId);

    int written = 0;
    for (CmsClient.CmsSection section : doc.sections()) {
      for (String piece : split(section.body())) {
        if (!screening.chunkIsSafe(piece)) {
          // The CMS refuses these at publish time (CMS-009); this is the second line of defence.
          log.warn("chunk of doc {} rejected as instruction-like", docId);
          continue;
        }
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocId(docId);
        chunk.setCollectionCode(doc.collectionCode());
        chunk.setLocale(doc.locale());
        chunk.setDocVersion(doc.version());
        chunk.setTitle(doc.title());
        chunk.setSection(section.heading());
        chunk.setContent(piece);
        chunk.setDeepLink(section.deepLink());
        chunk.setMetadata(
            objectMapper.writeValueAsString(
                Map.of(
                    "collection", doc.collectionCode(),
                    "docId", docId.toString(),
                    "locale", doc.locale(),
                    "version", doc.version(),
                    "section", section.heading() == null ? "" : section.heading())));
        // Flush before the vector write: the embedding is set with native SQL, so the row has to
        // exist in the database already or the UPDATE matches nothing.
        DocumentChunk saved = chunks.saveAndFlush(chunk);
        vectors.upsert(saved.getId(), embeddings.embed(section.heading() == null
            ? piece
            : section.heading() + "\n" + piece));
        written++;
      }
    }
    log.info("indexed doc {} into {} chunks", docId, written);
    return written;
  }

  @Override
  @Transactional
  public void remove(UUID docId) {
    chunks.deleteByDocId(docId);
  }

  @Override
  @Transactional
  public int reindex(String collectionCode) {
    int docs = 0;
    for (UUID docId : cms.publishedDocIds(collectionCode)) {
      index(docId);
      docs++;
    }
    return docs;
  }

  /**
   * The gap between "published" and "searchable".
   *
   * <p>A document normally reaches the index through the CMS publish webhook. Knowledge seeded by a
   * migration never fires one, so it sat in {@code cms.knowledge_doc} and was invisible to
   * retrieval — nine home-loan documents existed and answered nothing, because the only self-heal
   * was a backfill that gave up as soon as the index held any chunk at all.
   *
   * <p>Re-embedding everything on every tick would be wasteful and would churn vectors that are
   * already correct, so this indexes only what is missing.
   *
   * <p>{@code @Transactional} is on this method and not relied upon from {@link #index}: the call
   * below is a self-invocation, which does not pass through the Spring proxy, so the annotation on
   * {@code index} would not apply and the chunk delete failed with "No active transaction".
   */
  @Override
  @Transactional
  public int indexMissing() {
    Set<UUID> alreadyIndexed = new HashSet<>(chunks.findIndexedDocIds());
    int indexed = 0;
    for (UUID docId : cms.publishedDocIds(null)) {
      if (alreadyIndexed.contains(docId)) {
        continue;
      }
      try {
        if (index(docId) > 0) {
          indexed++;
        }
      } catch (RuntimeException e) {
        // One document that cannot be embedded must not cost the rest of the batch. Swallowed here
        // rather than propagating, so the transaction is not marked rollback-only and the documents
        // that did index still commit; the next tick retries this one.
        log.warn("could not index document {} on backfill: {}", docId, e.getMessage());
      }
    }
    return indexed;
  }

  /** Character windows with overlap, cut on a paragraph boundary when one is near. */
  private List<String> split(String body) {
    List<String> pieces = new ArrayList<>();
    if (body == null || body.isBlank()) {
      return pieces;
    }
    String text = body.strip();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(text.length(), start + CHUNK_CHARS);
      if (end < text.length()) {
        int paragraph = text.lastIndexOf("\n\n", end);
        if (paragraph > start + CHUNK_CHARS / 2) {
          end = paragraph;
        }
      }
      pieces.add(text.substring(start, end).strip());
      if (end >= text.length()) {
        break;
      }
      start = Math.max(start + 1, end - OVERLAP_CHARS);
    }
    return pieces;
  }
}
