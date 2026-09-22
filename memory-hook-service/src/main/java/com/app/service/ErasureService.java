package com.app.service;

import com.app.dto.request.MemoryRequests.ErasureRequestBody;
import com.app.dto.response.MemoryResponses.ErasureResponse;
import java.util.UUID;

/** Right to erasure: rows, vectors, snapshot, DEK and a Kafka tombstone (design §4). */
public interface ErasureService {

  ErasureResponse request(UUID customerId, ErasureRequestBody body, String actor);

  ErasureResponse status(UUID customerId, UUID erasureId);

  /** Queues a records-only erasure, used when {@code longTerm} consent is withdrawn. */
  void queueForConsentWithdrawal(String pseudoId, String actor);

  /** Executes one queued request; the job calls this, and so does the synchronous fast path. */
  void execute(UUID erasureId);

  boolean inProgress(String pseudoId);
}
