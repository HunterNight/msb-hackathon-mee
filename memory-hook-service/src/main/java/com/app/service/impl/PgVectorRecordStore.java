package com.app.service.impl;

import com.app.constant.MemoryConstants;
import com.app.service.RecordVectorStore;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** pgvector access for the record embeddings (design §8 {@code VectorStoreConfig}). */
@Service
public class PgVectorRecordStore implements RecordVectorStore {

  private final JdbcTemplate jdbc;

  public PgVectorRecordStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void upsert(UUID recordId, float[] embedding) {
    jdbc.update(
        "update memory_record set embedding = cast(? as vector) where id = ?",
        literal(embedding),
        recordId);
  }

  @Override
  public List<Hit> search(String pseudoId, float[] query, int topK) {
    // `1 - cosine distance` so a bigger score means a better match, as the API contract promises.
    return jdbc.query(
        """
        select id, 1 - (embedding <=> cast(? as vector)) as score
        from memory_record
        where pseudo_id = ?
          and embedding is not null
          and supersedes is null
          and (valid_until is null or valid_until > now())
        order by embedding <=> cast(? as vector)
        limit ?
        """,
        (rs, row) -> new Hit(rs.getObject("id", UUID.class), rs.getDouble("score")),
        literal(query),
        pseudoId,
        literal(query),
        Math.min(topK, MemoryConstants.RECALL_TOP_K_MAX));
  }

  @Override
  public int countFor(String pseudoId) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from memory_record where pseudo_id = ? and embedding is not null",
            Integer.class,
            pseudoId);
    return count == null ? 0 : count;
  }

  @Override
  public int clearFor(String pseudoId) {
    return jdbc.update(
        "update memory_record set embedding = null where pseudo_id = ?", pseudoId);
  }

  private String literal(float[] embedding) {
    StringJoiner joiner = new StringJoiner(",", "[", "]");
    for (float value : embedding) {
      joiner.add(Float.toString(value));
    }
    return joiner.toString();
  }
}
