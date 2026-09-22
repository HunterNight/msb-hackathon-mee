package com.app.service.tool;

import com.app.dto.internal.MiInternal.ToolResult;
import java.util.Map;
import java.util.UUID;

/**
 * One callable capability. The registry enforces the agent's allow-list and the Chat Pay switch
 * before {@code execute} is ever reached, so an implementation may assume it is permitted — but
 * never that its arguments are valid (design §12.3).
 */
public interface MiTool {

  String code();

  boolean mutating();

  ToolResult execute(Context context, Map<String, Object> args);

  /**
   * @param customerId the caller's identity, taken from the JWT and never from the model
   * @param stepUpToken present only on a confirmed execution, never during a proposal
   * @param screen where the customer is, so "this loan/card" resolves without asking (M3.12)
   */
  record Context(
      UUID customerId,
      UUID conversationId,
      UUID messageId,
      java.util.Locale locale,
      String stepUpToken,
      ScreenContext screen) {

    /** Convenience for the common case of no screen context (jobs, internal callers, tests). */
    public Context(
        UUID customerId,
        UUID conversationId,
        UUID messageId,
        java.util.Locale locale,
        String stepUpToken) {
      this(customerId, conversationId, messageId, locale, stepUpToken, null);
    }
  }

  /**
   * The screen the turn came from. {@code entityId} is client-supplied, so it is only ever used to
   * fill an argument the model left out, and the owning domain service still checks that the entity
   * belongs to {@code customerId} (05-security-model.md §4).
   */
  record ScreenContext(String screen, String entityType, UUID entityId) {

    public boolean hasEntity() {
      return entityType != null && entityId != null;
    }
  }
}
