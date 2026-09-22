package com.app.service.memory;

import com.app.dto.response.MiResponses.MemoryResponse;
import java.util.List;
import java.util.UUID;

/**
 * Mi's view of memory-hook-service. Recall results are injected as
 * {@code <untrusted source="memory">} and the customer sees exactly what is stored (design §9).
 */
public interface MemoryService {

  /** Abstract sentences to put in the prompt; empty when consent is off or the hook is down. */
  List<String> recallForPrompt(UUID customerId, String question);

  /**
   * Relevance-gated recall (BRD §6.2): only memory kinds allowed for the given intent are recalled.
   * Returns empty when the intent allows no memory, consent is off, or the hook is down.
   */
  List<String> recallForPrompt(UUID customerId, String question, String intent);

  MemoryResponse list(UUID customerId);

  boolean delete(UUID customerId, UUID recordId);

  int deleteAll(UUID customerId);

  boolean consent(UUID customerId);

  boolean setConsent(UUID customerId, boolean longTerm);

  /** Bucketed snapshot values that are safe to hand an agent. */
  java.util.Map<String, Object> promptSnapshot(UUID customerId);

  /** Exact aggregates, for the proactive policy engine only — never for a prompt. */
  java.util.Map<String, Object> triggerSnapshot(UUID customerId);

  com.app.dto.response.MiResponses.CandidateDecisionDto submitCandidate(
      UUID customerId, com.app.dto.response.MiResponses.CandidateRequestDto request);

  com.app.dto.response.MiResponses.MemoryRecordDto confirm(UUID customerId, UUID recordId);

  com.app.dto.response.MiResponses.MemoryRecordDto reject(UUID customerId, UUID recordId);

  com.app.dto.response.MiResponses.MemoryRecordDto edit(
      UUID customerId, UUID recordId, com.app.dto.response.MiResponses.EditMemoryRequest request);

  com.app.dto.response.MiResponses.MemoryRecordDto deactivate(UUID customerId, UUID recordId);

  boolean forget(UUID customerId, UUID recordId);

  com.app.dto.response.MiResponses.MemoryRecordDto nextHypothesis(UUID customerId);

  com.app.dto.response.MiResponses.MemoryGraphDto graph(UUID customerId);

  com.app.dto.response.MiResponses.MemoryWhyDto why(UUID customerId, UUID recordId);
}
