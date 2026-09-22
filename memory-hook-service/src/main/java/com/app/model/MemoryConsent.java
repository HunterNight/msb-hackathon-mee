package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Consent is evaluated on every ingest and every read, never cached beyond the request
 * (design §4 "Consent").
 */
@Entity
@Table(name = "memory_consent")
@Getter
@Setter
@NoArgsConstructor
public class MemoryConsent {

  @Id
  @Column(name = "pseudo_id", nullable = false, updatable = false)
  private String pseudoId;

  /** Without this there are no memory records at all — only snapshot aggregates. */
  @Column(name = "long_term", nullable = false)
  private boolean longTerm;

  @Column(name = "snapshot", nullable = false)
  private boolean snapshot = true;

  @Column(name = "improve_models", nullable = false)
  private boolean improveModels;

  @Column(name = "policy_version", nullable = false)
  private String policyVersion;

  @Column(name = "source", nullable = false)
  private String source;

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
