package com.app.model;

import com.app.constant.AgentCodes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One chat thread. {@code activeAgentCode} is where the router left the conversation. */
@Entity
@Table(name = "conversation")
@Getter
@Setter
@NoArgsConstructor
public class Conversation extends BaseEntity {

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "title")
  private String title;

  @Column(name = "active_agent_code", nullable = false)
  private String activeAgentCode = AgentCodes.GENERAL;

  @Column(name = "status", nullable = false)
  private String status = "OPEN";

  @Column(name = "last_message_at")
  private Instant lastMessageAt;
}
