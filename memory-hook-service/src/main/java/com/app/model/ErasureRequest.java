package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A right-to-erasure request and the proof produced when it completes (design §4 "Erasure"). */
@Entity
@Table(name = "erasure_request")
@Getter
@Setter
@NoArgsConstructor
public class ErasureRequest extends BaseEntity {

  @Column(name = "pseudo_id", nullable = false)
  private String pseudoId;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "requested_by", nullable = false)
  private String requestedBy;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt = Instant.now();

  @Column(name = "sla_at", nullable = false)
  private Instant slaAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "proof", columnDefinition = "jsonb")
  private String proof;
}
