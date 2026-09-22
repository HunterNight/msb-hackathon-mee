package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.repository.DocumentChunkRepository;
import com.app.service.client.CmsClient;
import com.app.service.rag.ChunkVectorStore;
import com.app.service.rag.EmbeddingService;
import com.app.service.security.InputScreeningService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Reconciling the vector index against what the CMS has published.
 *
 * <p>Found live: nine home-loan documents were published by a migration and answered nothing. The
 * publish webhook only fires for documents published through the CMS API, and the self-heal job
 * returned early whenever the index already held a chunk — so anything seeded after the first run
 * stayed invisible to retrieval forever.
 */
class KnowledgeIndexerMissingTest {

  private CmsClient cms;
  private DocumentChunkRepository chunks;
  private EmbeddingService embeddings;
  private KnowledgeIndexerImpl indexer;

  private static final UUID INDEXED = UUID.randomUUID();
  private static final UUID MISSING_A = UUID.randomUUID();
  private static final UUID MISSING_B = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    cms = Mockito.mock(CmsClient.class);
    chunks = Mockito.mock(DocumentChunkRepository.class);
    embeddings = Mockito.mock(EmbeddingService.class);
    InputScreeningService screening = Mockito.mock(InputScreeningService.class);
    when(screening.chunkIsSafe(any())).thenReturn(true);
    when(embeddings.embed(any())).thenReturn(new float[] {0.1f, 0.2f});
    // The real repository returns the persisted row; index() needs its generated id for the
    // vector write, so an unstubbed null would NPE before anything is counted.
    when(chunks.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              com.app.model.DocumentChunk chunk = invocation.getArgument(0);
              if (chunk.getId() == null) {
                chunk.setId(UUID.randomUUID());
              }
              return chunk;
            });

    indexer =
        new KnowledgeIndexerImpl(
            cms,
            chunks,
            Mockito.mock(ChunkVectorStore.class),
            embeddings,
            screening,
            new tools.jackson.databind.ObjectMapper());
  }

  /** A published document the CMS can describe, so {@code index} has something to chunk. */
  private void stubDocument(UUID docId, String title, String body) {
    when(cms.currentDoc(docId))
        .thenReturn(
            java.util.Optional.of(
                new CmsClient.CmsDocument(
                    docId,
                    "loan",
                    title,
                    "vi",
                    1,
                    List.of(new CmsClient.CmsSection(title, body, "msb://loans")))));
  }

  @Test
  @DisplayName("A published document with no chunks is indexed even when the index is not empty")
  void indexesMissingDocumentsWhenIndexIsNotEmpty() {
    when(chunks.findIndexedDocIds()).thenReturn(List.of(INDEXED));
    when(cms.publishedDocIds(null)).thenReturn(List.of(INDEXED, MISSING_A, MISSING_B));
    stubDocument(MISSING_A, "Vay mua nhà — tỷ lệ cho vay", "MSB cho vay tối đa 80% giá trị.");
    stubDocument(MISSING_B, "Vay mua nhà — lãi suất", "Lãi suất từ 7,5%/năm.");

    int indexed = indexer.indexMissing();

    assertThat(indexed).isEqualTo(2);
    // The already-indexed document must not be re-embedded: that would churn correct vectors and
    // spend an embedding call per tick for no gain.
    verify(cms, never()).currentDoc(INDEXED);
  }

  @Test
  @DisplayName("Nothing to do when every published document already has chunks")
  void doesNothingWhenAllIndexed() {
    when(chunks.findIndexedDocIds()).thenReturn(List.of(INDEXED, MISSING_A));
    when(cms.publishedDocIds(null)).thenReturn(List.of(INDEXED, MISSING_A));

    assertThat(indexer.indexMissing()).isZero();
    verify(embeddings, never()).embed(any());
  }

  @Test
  @DisplayName("An empty index is filled, which is the original bootstrap case")
  void bootstrapsAnEmptyIndex() {
    when(chunks.findIndexedDocIds()).thenReturn(List.of());
    when(cms.publishedDocIds(null)).thenReturn(List.of(MISSING_A));
    stubDocument(MISSING_A, "Vay mua nhà", "Hạn mức tối đa 10.000.000.000 đồng.");

    assertThat(indexer.indexMissing()).isEqualTo(1);
  }

  @Test
  @DisplayName("One document that cannot be embedded does not abort the rest of the batch")
  void oneBadDocumentDoesNotStopTheBatch() {
    when(chunks.findIndexedDocIds()).thenReturn(List.of());
    when(cms.publishedDocIds(null)).thenReturn(List.of(MISSING_A, MISSING_B));
    stubDocument(MISSING_A, "Broken", "body that fails to embed");
    stubDocument(MISSING_B, "Vay mua nhà — lãi suất", "Lãi suất từ 7,5%/năm.");

    // The embedding endpoint rejects the first document only.
    when(embeddings.embed(any()))
        .thenThrow(new IllegalStateException("embedding 404"))
        .thenReturn(new float[] {0.1f, 0.2f});

    // Without the per-document guard this threw and the healthy document never got indexed.
    assertThat(indexer.indexMissing()).isEqualTo(1);
  }
}
