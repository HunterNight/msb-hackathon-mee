package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Which events a record was derived from — ids only, so provenance survives without payloads. */
@Entity
@Table(name = "record_source")
@Getter
@Setter
@NoArgsConstructor
public class RecordSource extends BaseEntity {

  @Column(name = "record_id", nullable = false)
  private UUID recordId;

  @Column(name = "event_id", nullable = false)
  private String eventId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "event_time", nullable = false)
  private Instant eventTime;
}
