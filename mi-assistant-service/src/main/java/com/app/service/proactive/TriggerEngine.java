package com.app.service.proactive;

import java.util.UUID;

/**
 * Evaluates the CMS trigger rules against a customer's snapshot and emits insights and nudges,
 * each with the explanation the design requires (guideline 06 §5).
 */
public interface TriggerEngine {

  int evaluate(UUID customerId);

  /** Dismissal feeds the cooldown, and eventually memory-hook's PREFERENCE extractor. */
  boolean dismiss(UUID customerId, UUID nudgeId);
}
