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
 * The customer's data-encryption key, wrapped by the KEK. Deleting this row crypto-shreds every
 * encrypted field the customer owns, which is the first thing erasure does (design §4).
 */
@Entity
@Table(name = "customer_key")
@Getter
@Setter
@NoArgsConstructor
public class CustomerKey {

  /** HMAC(customerId, MEMORY_PSEUDONYM_KEY) — the namespace used everywhere else. */
  @Id
  @Column(name = "pseudo_id", nullable = false, updatable = false)
  private String pseudoId;

  /** A second HMAC under a different key: the only lookup path from a request's customerId. */
  @Column(name = "customer_id_hmac", nullable = false, unique = true)
  private String customerIdHmac;

  @Column(name = "dek_wrapped", nullable = false)
  private byte[] dekWrapped;

  @Column(name = "kek_version", nullable = false)
  private String kekVersion;

  @Column(name = "rotated_at")
  private Instant rotatedAt;

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
