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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A durable, human-supervised workflow: steps, approvals and a trail (design §M3.10). */
@Entity
@Table(name = "plan")
@Getter
@Setter
@NoArgsConstructor
public class Plan extends BaseEntity {

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 0)
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "log", nullable = false, columnDefinition = "jsonb")
  private String log = "[]";

  @Column(name = "expires_at")
  private Instant expiresAt;
}
