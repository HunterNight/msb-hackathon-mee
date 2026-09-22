package com.app.service;

import com.app.dto.request.MemoryRequests.CandidateRequest;
import com.app.dto.request.MemoryRequests.EditRequest;
import com.app.dto.request.MemoryRequests.RememberRequest;
import com.app.dto.response.MemoryResponses.MemoryRecordDto;
import com.app.model.MemoryRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Upsert with supersession, TTL and the DLP gate — the only writer of {@code memory_record}. */
public interface MemoryRecordService {

  /** Mi's {@code remember} tool: capped per day and marked {@code source=CHAT}. */
  MemoryRecordDto remember(UUID customerId, RememberRequest request, String actor);

  /**
   * Machine-extracted candidate. Returns empty when the DLP gate or the confidence floor rejects
   * it, because one bad candidate must never fail a whole event batch.
   */
  java.util.Optional<MemoryRecord> upsert(Candidate candidate);

  /** Mi submits a classified candidate from chat; returns the decision. */
  CandidateDecision submitCandidate(UUID customerId, CandidateRequest request, String actor);

  /** Customer confirmed a candidate/hypothesis (card tap or answer). */
  MemoryRecordDto confirm(UUID customerId, UUID recordId, String actor);

  /** Customer rejected a hypothesis; sets suppressed_until. */
  MemoryRecordDto reject(UUID customerId, UUID recordId, String actor);

  /** Customer edited a record ("Sửa"). */
  MemoryRecordDto edit(UUID customerId, UUID recordId, EditRequest request, String actor);

  /** Customer said "không còn…" — marks INACTIVE, not deleted. */
  MemoryRecordDto deactivate(UUID customerId, UUID recordId, String actor);

  /** Customer said "Đừng nhớ chuyện này" — crypto-shred (FORGOTTEN). */
  void forget(UUID customerId, UUID recordId, String actor);

  /** Next validation question to ask (at most one per session). */
  java.util.Optional<MemoryRecordDto> nextHypothesis(UUID customerId, String actor);

  /** Customer-safe evidence summary ("Tại sao MEE nghĩ vậy?"). */
  String why(UUID customerId, UUID recordId, String actor);

  /** Memory Universe graph: nodes + links for confirmed/hypothesis/inactive/expired. */
  com.app.dto.response.MemoryResponses.MemoryGraphResponse graph(UUID customerId, String actor);

  Page<MemoryRecordDto> list(UUID customerId, String kind, Pageable pageable);

  void delete(UUID customerId, UUID recordId, String actor);

  MemoryRecordDto toDto(MemoryRecord record, byte[] dek);

  /** Expires records past their {@code validUntil}; run by the retention job. */
  int expire(Instant now, int batchSize);

  /**
   * @param normalisedKey identity of the fact within its kind; a new candidate with the same key
   *     supersedes the previous record rather than being merged into it
   */
  record Candidate(
      String pseudoId,
      byte[] dek,
      String kind,
      String text,
      String normalisedKey,
      BigDecimal confidence,
      String source,
      Map<String, String> refs,
      String eventId,
      String eventType,
      Instant eventTime) {}

  record CandidateDecision(String decision, MemoryRecordDto record) {}
}
