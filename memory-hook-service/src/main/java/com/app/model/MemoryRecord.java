package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One semantic fact about the customer. {@code textEnc} is the exact sentence under the customer
 * DEK; {@code abstractText} is the sanitised form that is embedded and is the only text ever
 * returned to Mi (design §3).
 */
@Entity
@Table(name = "memory_record")
@Getter
@Setter
@NoArgsConstructor
public class MemoryRecord extends BaseEntity {

  @Column(name = "pseudo_id", nullable = false)
  private String pseudoId;

  @Column(name = "kind", nullable = false)
  private String kind;

  @Column(name = "text_enc", nullable = false)
  private byte[] textEnc;

  @Column(name = "abstract_text", nullable = false)
  private String abstractText;

  @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
  private BigDecimal confidence;

  @Column(name = "source", nullable = false)
  private String source;

  @Column(name = "pinned", nullable = false)
  private boolean pinned;

  /** Set on the older row when a newer record replaces it; nothing is merged silently. */
  @Column(name = "supersedes")
  private UUID supersedes;

  /** Stable identity of the fact within its kind, e.g. the beneficiary id for a NICKNAME. */
  @Column(name = "normalised_key", nullable = false)
  private String normalisedKey;

  /** Encrypted `{beneficiaryId|goalId|triggerCode}` — ids only, never account numbers. */
  @Column(name = "refs_enc")
  private byte[] refsEnc;

  @Column(name = "classification", nullable = false)
  private String classification;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom = Instant.now();

  @Column(name = "valid_until")
  private Instant validUntil;

  @Column(name = "state", nullable = false)
  private String state = com.app.constant.MemoryConstants.STATE_HYPOTHESIS;

  @Column(name = "explicit", nullable = false)
  private boolean explicit;

  @Column(name = "customer_confirmed", nullable = false)
  private boolean customerConfirmed;

  @Column(name = "last_confirmed_at")
  private Instant lastConfirmedAt;

  @Column(name = "persistence", nullable = false)
  private String persistence = com.app.constant.MemoryConstants.PERSIST_LONG_TERM;

  @Column(name = "entity")
  private String entity;

  @Column(name = "reason_enc")
  private byte[] reasonEnc;

  @Column(name = "evidence_summary")
  private String evidenceSummary;

  @Column(name = "suppressed_until")
  private Instant suppressedUntil;
}
