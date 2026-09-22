package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Aggregates, no free text. {@code publicSlice} is bucketed and safe to put in a prompt;
 * {@code snapshotEnc} holds the exact numbers only the proactive policy engine sees (design §3).
 */
@Entity
@Table(name = "customer_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class CustomerSnapshot {

  @Id
  @Column(name = "pseudo_id", nullable = false, updatable = false)
  private String pseudoId;

  @Column(name = "snapshot_enc", nullable = false)
  private byte[] snapshotEnc;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "public_slice", nullable = false, columnDefinition = "jsonb")
  private String publicSlice = "{}";

  @Column(name = "snapshot_version", nullable = false)
  private int snapshotVersion = 1;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "created_by", length = 64, updatable = false)
  private String createdBy;

  @jakarta.persistence.Version
  @Column(name = "version", nullable = false)
  private long version;
}
