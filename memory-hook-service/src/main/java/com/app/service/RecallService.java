package com.app.service;

import com.app.dto.response.MemoryResponses.RecallResponse;
import java.util.List;
import java.util.UUID;

/**
 * Similarity recall over one customer's namespace. Without {@code longTerm} consent it returns an
 * empty result rather than an error, so agents degrade silently (api contract).
 */
public interface RecallService {

  RecallResponse recall(
      UUID customerId, String question, int topK, List<String> kinds, boolean strict, String actor);
}
