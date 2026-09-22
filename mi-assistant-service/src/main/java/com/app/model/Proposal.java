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

/**
 * The only path from a conversation to money moving. A row exists before anything is executed, and
 * execution always carries {@code idempotencyKey} = this id (design §3.2).
 */
@Entity
@Table(name = "proposal")
@Getter
@Setter
@NoArgsConstructor
public class Proposal extends BaseEntity {

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "message_id")
  private UUID messageId;

  @Column(name = "conversation_id")
  private UUID conversationId;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "amount", nullable = false, precision = 19, scale = 0)
  private BigDecimal amount = BigDecimal.ZERO;

  /** Validated tool arguments — never free text from the model. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "params", nullable = false, columnDefinition = "jsonb")
  private String params = "{}";

  /** The rendered card, so a poll returns exactly what the stream sent. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "card", nullable = false, columnDefinition = "jsonb")
  private String card = "{}";

  @Column(name = "phase", nullable = false)
  private String phase;

  @Column(name = "ref")
  private String ref;

  @Column(name = "executed_by")
  private String executedBy;

  @Column(name = "requires_step_up", nullable = false)
  private boolean requiresStepUp = true;

  @Column(name = "step_up_scope")
  private String stepUpScope;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
}
