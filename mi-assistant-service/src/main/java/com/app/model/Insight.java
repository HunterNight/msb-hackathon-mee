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

/** The MiInsightBanner text for one placement, produced by the trigger engine. */
@Entity
@Table(name = "insight")
@Getter
@Setter
@NoArgsConstructor
public class Insight extends BaseEntity {

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "placement", nullable = false)
  private String placement;

  @Column(name = "text_key", nullable = false)
  private String textKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "args", nullable = false, columnDefinition = "jsonb")
  private String args = "{}";

  @Column(name = "prompt")
  private String prompt;

  @Column(name = "deep_link")
  private String deepLink;

  @Column(name = "action_key")
  private String actionKey;

  @Column(name = "tone", nullable = false)
  private String tone = "DEFAULT";

  @Column(name = "trigger_code")
  private String triggerCode;

  @Column(name = "valid_until")
  private Instant validUntil;
}
