package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The minimised residue of one event: what kind of money movement, against which opaque
 * counterparty key, how much, which day. Merchant strings, notes and account numbers never get
 * here, so the recurring/salary rules can count without retaining raw events (design §4).
 */
@Entity
@Table(name = "event_fact")
@Getter
@Setter
@NoArgsConstructor
public class EventFact extends BaseEntity {

  @Column(name = "pseudo_id", nullable = false)
  private String pseudoId;

  @Column(name = "fact_type", nullable = false)
  private String factType;

  /** Opaque counterparty/aggregate key (a beneficiary id, biller code or category). */
  @Column(name = "ref_key", nullable = false)
  private String refKey;

  @Column(name = "amount", precision = 19, scale = 0)
  private BigDecimal amount;

  @Column(name = "category_code")
  private String categoryCode;

  @Column(name = "day_of_month")
  private Integer dayOfMonth;

  /** Progress percentage for goal facts; null everywhere else. */
  @Column(name = "pct")
  private Integer pct;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "event_id", nullable = false)
  private String eventId;
}
