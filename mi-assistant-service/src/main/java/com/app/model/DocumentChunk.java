package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One retrievable passage of a CMS document. The {@code embedding} column is deliberately absent
 * from this mapping: it is written and searched with native SQL so the entity stays portable.
 */
@Entity
@Table(name = "document_chunk")
@Getter
@Setter
@NoArgsConstructor
public class DocumentChunk extends BaseEntity {

  @Column(name = "doc_id", nullable = false)
  private UUID docId;

  @Column(name = "collection_code", nullable = false)
  private String collectionCode;

  @Column(name = "locale", nullable = false)
  private String locale;

  @Column(name = "doc_version", nullable = false)
  private int docVersion;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "section")
  private String section;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  /** Present on how-to chunks: what Mi turns into the action chip under the steps (design §3.1). */
  @Column(name = "deep_link")
  private String deepLink;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private String metadata = "{}";
}
