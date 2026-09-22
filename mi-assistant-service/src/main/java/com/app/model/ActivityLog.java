package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Screen 10. Keys and arguments only — the sentence is rendered per request locale. */
@Entity
@Table(name = "activity_log")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog extends BaseEntity {

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "kind", nullable = false)
  private String kind;

  @Column(name = "title_key", nullable = false)
  private String titleKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "title_args", nullable = false, columnDefinition = "jsonb")
  private String titleArgs = "{}";

  @Column(name = "subtitle_key", nullable = false)
  private String subtitleKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "subtitle_args", nullable = false, columnDefinition = "jsonb")
  private String subtitleArgs = "{}";

  @Column(name = "proposal_id")
  private UUID proposalId;

  @Column(name = "ref")
  private String ref;

  @Column(name = "deep_link")
  private String deepLink;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();
}
