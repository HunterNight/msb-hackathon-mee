package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A rejected event. The body is stored redacted so ops can triage without seeing raw PII. */
@Entity
@Table(name = "dlq_entry")
@Getter
@Setter
@NoArgsConstructor
public class DlqEntry extends BaseEntity {

  @Column(name = "event_id", nullable = false)
  private String eventId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "redacted_body", nullable = false)
  private String redactedBody;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "attempts", nullable = false)
  private int attempts = 1;

  @Column(name = "at", nullable = false)
  private Instant at = Instant.now();
}
