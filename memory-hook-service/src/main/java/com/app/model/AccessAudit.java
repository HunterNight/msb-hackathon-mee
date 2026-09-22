package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Every read of customer memory leaves a row here (design §4 "Access"). */
@Entity
@Table(name = "access_audit")
@Getter
@Setter
@NoArgsConstructor
public class AccessAudit {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id = BaseEntity.newId();

  @Column(name = "pseudo_id", nullable = false)
  private String pseudoId;

  @Column(name = "actor", nullable = false)
  private String actor;

  @Column(name = "scope", nullable = false)
  private String scope;

  @Column(name = "operation", nullable = false)
  private String operation;

  @Column(name = "records", nullable = false)
  private int records;

  @Column(name = "request_id")
  private String requestId;

  @Column(name = "at", nullable = false)
  private Instant at = Instant.now();
}
