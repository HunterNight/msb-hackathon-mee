package com.app.service;

import com.app.dto.response.MemoryResponses.SnapshotPromptSlice;
import com.app.dto.response.MemoryResponses.SnapshotTriggerSlice;
import java.util.UUID;

/**
 * Aggregates distilled from {@code event_fact}. The prompt slice is bucketed; the trigger slice
 * carries exact numbers and goes only to the proactive policy engine (design §3).
 */
public interface SnapshotService {

  /** Recomputes and persists both slices after an event changed something material. */
  void rebuild(String pseudoId, byte[] dek);

  SnapshotPromptSlice promptSlice(UUID customerId, String actor);

  SnapshotTriggerSlice triggerSlice(UUID customerId, String actor);

  SnapshotPromptSlice promptSliceForExport(String pseudoId);
}
