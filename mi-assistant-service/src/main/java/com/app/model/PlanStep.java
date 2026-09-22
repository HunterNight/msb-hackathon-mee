package com.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plan_step")
@Getter
@Setter
@NoArgsConstructor
public class PlanStep extends BaseEntity {

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  @Column(name = "seq", nullable = false)
  private int seq;

  @Column(name = "kind", nullable = false)
  private String kind;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "amount", precision = 19, scale = 0)
  private BigDecimal amount;

  @Column(name = "requires_approval", nullable = false)
  private boolean requiresApproval = true;

  @Column(name = "proposal_id")
  private UUID proposalId;

  /** The event this step is blocked on, e.g. {@code msb.account.salary.credited.v1}. */
  @Column(name = "waiting_for")
  private String waitingFor;
}
