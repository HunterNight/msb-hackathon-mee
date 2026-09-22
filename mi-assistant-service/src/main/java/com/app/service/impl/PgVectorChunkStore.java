package com.app.service.impl;

import com.app.service.rag.ChunkVectorStore;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PgVectorChunkStore implements ChunkVectorStore {

  private final JdbcTemplate jdbc;

  public PgVectorChunkStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void upsert(UUID chunkId, float[] embedding) {
    jdbc.update(
        "update document_chunk set embedding = cast(? as vector) where id = ?",
        literal(embedding),
        chunkId);
  }

  @Override
  public List<Hit> search(List<String> collectionCodes, String locale, float[] query, int topK) {
    if (collectionCodes == null || collectionCodes.isEmpty()) {
      return List.of();
    }
    String placeholders = String.join(",", java.util.Collections.nCopies(collectionCodes.size(), "?"));
    // The locale filter falls back to vi, which is what the design asks for when a document has
    // not been translated yet.
    String sql =
        """
        select id, 1 - (embedding <=> cast(? as vector)) as score
        from document_chunk
        where embedding is not null
          and collection_code in (%s)
          and locale in (?, 'vi')
        order by embedding <=> cast(? as vector)
        limit ?
        """
            .formatted(placeholders);

    Object[] args = new Object[collectionCodes.size() + 4];
    int index = 0;
    args[index++] = literal(query);
    for (String code : collectionCodes) {
      args[index++] = code;
    }
    args[index++] = locale;
    args[index++] = literal(query);
    args[index] = topK;

    return jdbc.query(
        sql, (rs, row) -> new Hit(rs.getObject("id", UUID.class), rs.getDouble("score")), args);
  }

  private String literal(float[] embedding) {
    StringJoiner joiner = new StringJoiner(",", "[", "]");
    for (float value : embedding) {
      joiner.add(Float.toString(value));
    }
    return joiner.toString();
  }
}
