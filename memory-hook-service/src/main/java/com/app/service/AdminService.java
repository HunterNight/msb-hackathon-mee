package com.app.service;

import com.app.dto.request.MemoryRequests.KeyRotationRequest;
import com.app.dto.request.MemoryRequests.ReplayRequest;
import com.app.dto.response.MemoryResponses.DlqRow;
import com.app.dto.response.MemoryResponses.KeyRotationResponse;
import com.app.dto.response.MemoryResponses.PoisoningMetrics;
import com.app.dto.response.MemoryResponses.ReplayResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Ops surface: replay, DLQ triage, poisoning metrics and key rotation (design §2 H9). */
public interface AdminService {

  ReplayResponse replay(ReplayRequest request);

  Page<DlqRow> dlq(Pageable pageable);

  String retry(UUID id);

  void discard(UUID id);

  PoisoningMetrics poisoning(Instant from, Instant to);

  KeyRotationResponse rotate(KeyRotationRequest request);
}
