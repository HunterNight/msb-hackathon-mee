package com.app.model;

import com.app.constant.MiConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A spending cap the customer set through Mi; no money moves, so it is always AUTO. */
@Entity
@Table(name = "budget")
@Getter
@Setter
@NoArgsConstructor
public class Budget extends BaseEntity {

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "category_code", nullable = false)
  private String categoryCode;

  @Column(name = "monthly_limit", nullable = false, precision = 19, scale = 0)
  private BigDecimal monthlyLimit;

  @Column(name = "alert_pct", nullable = false)
  private int alertPct = MiConstants.BUDGET_ALERT_PCT_DEFAULT;

  @Column(name = "active", nullable = false)
  private boolean active = true;
}
