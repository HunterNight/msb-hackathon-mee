package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "memory_link")
@Getter
@Setter
@NoArgsConstructor
public class MemoryLink extends BaseEntity {

  @Column(name = "pseudo_id", nullable = false)
  private String pseudoId;

  @Column(name = "from_record", nullable = false)
  private UUID fromRecord;

  @Column(name = "to_record", nullable = false)
  private UUID toRecord;

  @Column(name = "relation", nullable = false)
  private String relation;

  @Column(name = "state", nullable = false)
  private String state;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;
}
