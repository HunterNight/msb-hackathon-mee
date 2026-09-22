package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ids and status only. This is what makes a duplicate Kafka delivery a no-op (design §4). */
@Entity
@Table(name = "ingest_log")
@Getter
@Setter
@NoArgsConstructor
public class IngestLog {

  @Id
  @Column(name = "event_id", nullable = false, updatable = false)
  private String eventId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "pseudo_id")
  private String pseudoId;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "reason")
  private String reason;

  @Column(name = "at", nullable = false)
  private Instant at = Instant.now();
}
