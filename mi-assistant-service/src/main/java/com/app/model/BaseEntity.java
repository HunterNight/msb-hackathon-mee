package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Root of every persistent aggregate: UUID v7 primary key generated in Java, audit columns and an
 * optimistic-locking version (guideline 01 §7).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

  private static final SecureRandom RANDOM = new SecureRandom();

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id = newId();

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private Instant updatedAt;

  @CreatedBy
  @Column(name = "created_by", length = 64, updatable = false)
  private String createdBy;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  /**
   * RFC 9562 UUID version 7: 48-bit big-endian Unix epoch millisecond prefix, then random bits.
   * Time-ordered ids keep B-tree indexes dense, which matters for the high-write ledger tables.
   */
  public static UUID newId() {
    long millis = System.currentTimeMillis();
    byte[] random = new byte[10];
    RANDOM.nextBytes(random);

    long msb = (millis & 0xFFFFFFFFFFFFL) << 16;
    msb |= 0x7000L; // version 7
    msb |= ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);

    long lsb = 0x8000000000000000L; // variant 10xx
    lsb |= ((long) (random[2] & 0x3F)) << 56;
    for (int i = 3; i < 10; i++) {
      lsb |= ((long) (random[i] & 0xFF)) << ((9 - i) * 8);
    }
    return new UUID(msb, lsb);
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public long getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BaseEntity that)) {
      return false;
    }
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
