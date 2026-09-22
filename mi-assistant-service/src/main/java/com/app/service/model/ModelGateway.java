package com.app.service.model;

import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.ModelReply;
import com.app.dto.internal.MiInternal.RouteDecision;
import com.app.dto.internal.MiInternal.ToolSpec;
import java.util.List;
import java.util.Locale;

/**
 * The single door to the language model: profile selection, fallback chain and the PII gate all
 * live behind it, so no caller ever holds a model handle (guideline 06 §8).
 */
public interface ModelGateway {

  /**
   * @param toolsAvailable the agent's allow-list, already emptied when the turn is guarded
   * @return the reply text and, at most, one tool call the orchestrator still has to validate
   */
  ModelReply chat(
      AgentSpec agent,
      String systemPrompt,
      List<Turn> history,
      String userTurn,
      List<ToolSpec> toolsAvailable,
      Locale locale);

  /** The cheap classification call that drives hand-off (design §3.3). */
  RouteDecision classify(String text, List<Turn> history, Locale locale);

  record Turn(String role, String text) {}
}
