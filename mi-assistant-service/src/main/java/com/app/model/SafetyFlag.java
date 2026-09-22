package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One refused turn. Three within 24h flag the customer for fraud review (design §12.4). */
@Entity
@Table(name = "safety_flag")
@Getter
@Setter
@NoArgsConstructor
public class SafetyFlag extends BaseEntity {

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "label", nullable = false)
  private String label;

  /** Redacted and truncated: enough for review, not a copy of the request. */
  @Column(name = "excerpt")
  private String excerpt;

  @Column(name = "at", nullable = false)
  private Instant at = Instant.now();
}
