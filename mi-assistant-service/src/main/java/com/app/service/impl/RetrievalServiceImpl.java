package com.app.service.impl;

import com.app.constant.MiConstants;
import com.app.dto.internal.MiInternal.RetrievedChunk;
import com.app.model.DocumentChunk;
import com.app.repository.DocumentChunkRepository;
import com.app.service.rag.ChunkVectorStore;
import com.app.service.rag.EmbeddingService;
import com.app.service.rag.RetrievalService;
import com.app.service.security.InputScreeningService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetrievalServiceImpl implements RetrievalService {

  private final ChunkVectorStore vectors;
  private final double similarityThreshold;
  private final EmbeddingService embeddings;
  private final DocumentChunkRepository chunks;
  private final InputScreeningService screening;

  public RetrievalServiceImpl(
      ChunkVectorStore vectors,
      EmbeddingService embeddings,
      DocumentChunkRepository chunks,
      InputScreeningService screening,
      @org.springframework.beans.factory.annotation.Value(
              "${app.mi.rag.similarity-threshold:" + MiConstants.RAG_SIMILARITY_THRESHOLD + "}")
          double similarityThreshold) {
    this.vectors = vectors;
    this.embeddings = embeddings;
    this.chunks = chunks;
    this.screening = screening;
    // The guideline's 0.72 assumes a real embedding model; the local hashing embedder scores
    // lower, so the floor is configurable rather than hard-coded.
    this.similarityThreshold = similarityThreshold;
  }

  @Override
  @Transactional(readOnly = true)
  public List<RetrievedChunk> search(
      List<String> collectionCodes, String question, Locale locale, int topK) {

    float[] query = embeddings.embed(question);
    List<RetrievedChunk> results = new ArrayList<>();
    for (ChunkVectorStore.Hit hit :
        vectors.search(collectionCodes, locale.getLanguage(), query, topK)) {

      Optional<DocumentChunk> chunk = chunks.findById(hit.chunkId());
      if (chunk.isEmpty()) {
        continue;
      }
      // Screened again at read time: a document published before the indexer's rules tightened
      // must not become an instruction now (design §12.2).
      if (!screening.chunkIsSafe(chunk.get().getContent())) {
        continue;
      }
      results.add(
          new RetrievedChunk(
              chunk.get().getDocId(),
              chunk.get().getTitle(),
              chunk.get().getSection(),
              chunk.get().getContent(),
              chunk.get().getDeepLink(),
              chunk.get().getCollectionCode(),
              hit.score()));
    }
    return results;
  }

  @Override
  public boolean belowThreshold(List<RetrievedChunk> chunks) {
    return chunks.stream().noneMatch(chunk -> chunk.score() >= similarityThreshold);
  }
}
