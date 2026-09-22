package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A proactive item with its explanation. Dismissals feed the trigger cooldown and, through
 * memory-hook, eventually become a stated preference.
 */
@Entity
@Table(name = "nudge")
@Getter
@Setter
@NoArgsConstructor
public class Nudge extends BaseEntity {

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "trigger_code", nullable = false)
  private String triggerCode;

  @Column(name = "placement", nullable = false)
  private String placement;

  @Column(name = "text_key", nullable = false)
  private String textKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "args", nullable = false, columnDefinition = "jsonb")
  private String args = "{}";

  @Column(name = "explain_key", nullable = false)
  private String explainKey;

  @Column(name = "prompt")
  private String prompt;

  @Column(name = "deep_link")
  private String deepLink;

  @Column(name = "dismissed", nullable = false)
  private boolean dismissed;

  @Column(name = "dismissed_at")
  private Instant dismissedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
}
