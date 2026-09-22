package com.app.service.agent;

import com.app.dto.internal.MiInternal.AgentSpec;
import java.math.BigDecimal;

/** Per-agent guardrails, the citation guard and the output allow-lists (design §12.3). */
public interface GuardrailService {

  /**
   * @return the reply, possibly rewritten; throws MI-011 when the agent's guardrail blocks it
   */
  default String checkReply(AgentSpec agent, String reply, boolean citedOrToolBacked) {
    return checkReply(agent, reply, citedOrToolBacked, false);
  }

  /**
   * @param memoryUsed whether this turn injected customer memory into the prompt — the
   *     surveillance-phrasing check (BRD §5.3a) only applies then
   */
  String checkReply(AgentSpec agent, String reply, boolean citedOrToolBacked, boolean memoryUsed);

  /** The per-agent ceiling, which binds regardless of the customer's own auto limit. */
  void checkProposalAmount(AgentSpec agent, BigDecimal amount);

  /** Deep links must be in the app's route table; anything else is stripped. */
  String sanitiseLinks(String reply);
}
