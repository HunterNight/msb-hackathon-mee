package com.app.model;

import com.app.constant.MiConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Screen 09: what Mi may do without asking, and the ceiling on it (design §3.2). */
@Entity
@Table(name = "customer_autonomy")
@Getter
@Setter
@NoArgsConstructor
public class CustomerAutonomy {

  @Id
  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "perm_recurring_bills", nullable = false)
  private boolean permRecurringBills;

  @Column(name = "perm_saved_recipients", nullable = false)
  private boolean permSavedRecipients;

  @Column(name = "perm_auto_saving", nullable = false)
  private boolean permAutoSaving;

  @Column(name = "perm_home_insights", nullable = false)
  private boolean permHomeInsights = true;

  @Column(name = "auto_limit", nullable = false, precision = 19, scale = 0)
  private BigDecimal autoLimit = MiConstants.AUTO_LIMIT_DEFAULT;

  @Column(name = "paused", nullable = false)
  private boolean paused;

  @Column(name = "proactive", nullable = false)
  private boolean proactive = true;

  @Column(name = "memory_long_term", nullable = false)
  private boolean memoryLongTerm;

  @Column(name = "improve_models", nullable = false)
  private boolean improveModels;

  @Column(name = "policy_version", nullable = false)
  private String policyVersion = MiConstants.POLICY_VERSION;

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
