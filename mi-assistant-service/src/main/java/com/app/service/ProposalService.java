package com.app.service;

import com.app.dto.response.MiResponses.KeyValueRow;
import com.app.dto.response.MiResponses.ProposalCardDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Propose → confirm → execute. A proposal row exists before anything moves, execution is
 * idempotent on the proposal id, and the model never touches this class (design §3.2).
 */
public interface ProposalService {

  ProposalCardDto propose(Draft draft);

  ProposalCardDto confirm(UUID customerId, UUID proposalId, String stepUpToken, boolean acceptMismatch);

  ProposalCardDto cancel(UUID customerId, UUID proposalId);

  ProposalCardDto get(UUID customerId, UUID proposalId);

  /** Turns overdue PROPOSE rows into EXPIRED plus the BLOCKED activity entry. */
  int expireOverdue(int batchSize);

  record Draft(
      UUID customerId,
      UUID conversationId,
      UUID messageId,
      String type,
      String titleKey,
      BigDecimal amount,
      List<KeyValueRow> rows,
      Map<String, Object> params,
      String stepUpScope,
      boolean beneficiarySaved,
      java.util.Locale locale) {}
}
