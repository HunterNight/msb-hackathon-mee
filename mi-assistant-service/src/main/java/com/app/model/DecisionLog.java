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

/** What {@code GET /explain/{decisionId}} answers from: why Mi did or suggested something. */
@Entity
@Table(name = "decision_log")
@Getter
@Setter
@NoArgsConstructor
public class DecisionLog extends BaseEntity {

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "kind", nullable = false)
  private String kind;

  @Column(name = "reason_key", nullable = false)
  private String reasonKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "inputs", nullable = false, columnDefinition = "jsonb")
  private String inputs = "[]";

  @Column(name = "policy_name")
  private String policyName;

  @Column(name = "policy_version")
  private String policyVersion;

  @Column(name = "model_profile")
  private String modelProfile;

  @Column(name = "model_version")
  private String modelVersion;
}
