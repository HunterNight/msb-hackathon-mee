package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A single turn. {@code payload} carries the card, chart or steps block; {@code text} is stored
 * with PII masked, because the CMS evaluation view reads these rows (design §12.5).
 */
@Entity
@Table(name = "message")
@Getter
@Setter
@NoArgsConstructor
public class Message extends BaseEntity {

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "role", nullable = false)
  private String role;

  @Column(name = "kind", nullable = false)
  private String kind;

  @Column(name = "text", columnDefinition = "text")
  private String text;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  private String payload;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "chips", nullable = false, columnDefinition = "jsonb")
  private String chips = "[]";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "citations", nullable = false, columnDefinition = "jsonb")
  private String citations = "[]";

  @Column(name = "agent_code")
  private String agentCode;

  @Column(name = "safety")
  private String safety;

  @Column(name = "seq", nullable = false)
  private int seq;
}
