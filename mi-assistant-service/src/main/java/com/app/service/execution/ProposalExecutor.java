package com.app.service.execution;

import java.util.Map;
import java.util.UUID;

/**
 * Dispatches a confirmed proposal to the domain service that owns it. The proposal id is the
 * idempotency key on every call, so a retry can never move money twice (design §3.2).
 */
public interface ProposalExecutor {

  Outcome execute(
      String type,
      UUID customerId,
      UUID proposalId,
      Map<String, Object> params,
      String executedBy,
      String stepUpToken);

  record Outcome(String ref, String status) {}
}
