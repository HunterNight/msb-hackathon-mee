package com.app.service;

import com.app.dto.request.MemoryRequests.ConsentRequest;
import com.app.dto.response.MemoryResponses.ConsentResponse;
import com.app.model.MemoryConsent;

/**
 * Consent is checked on every ingest and every read. Withdrawing {@code longTerm} queues an
 * erasure of the records; withdrawing {@code snapshot} deletes the snapshot (design §4).
 */
public interface ConsentService {

  MemoryConsent forPseudo(String pseudoId);

  boolean allowsRecords(String pseudoId);

  boolean allowsSnapshot(String pseudoId);

  ConsentResponse get(java.util.UUID customerId);

  ConsentResponse update(java.util.UUID customerId, ConsentRequest request, String actor);
}
