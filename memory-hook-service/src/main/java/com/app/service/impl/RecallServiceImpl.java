package com.app.service.impl;

import com.app.constant.ErrorCode;
import com.app.constant.MemoryConstants;
import com.app.dto.response.MemoryResponses.ConsentFlags;
import com.app.dto.response.MemoryResponses.MemoryRecordDto;
import com.app.dto.response.MemoryResponses.RecallResponse;
import com.app.dto.response.MemoryResponses.ScoredRecord;
import com.app.exception.BusinessException;
import com.app.model.MemoryRecord;
import com.app.repository.MemoryRecordRepository;
import com.app.service.AuditService;
import com.app.service.ConsentService;
import com.app.service.EmbeddingService;
import com.app.service.MemoryRecordService;
import com.app.service.RecallService;
import com.app.service.RecordVectorStore;
import com.app.service.crypto.KeyService;
import com.app.service.privacy.PiiRedactor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecallServiceImpl implements RecallService {

  private static final String OP_RECALL = "RECALL";
  private static final String RATE_KEY = "memory:recall:";

  private final MemoryRecordRepository records;
  private final MemoryRecordService recordService;
  private final ConsentService consentService;
  private final KeyService keyService;
  private final EmbeddingService embeddings;
  private final RecordVectorStore vectors;
  private final PiiRedactor redactor;
  private final AuditService auditService;
  private final StringRedisTemplate redis;

  public RecallServiceImpl(
      MemoryRecordRepository records,
      MemoryRecordService recordService,
      ConsentService consentService,
      KeyService keyService,
      EmbeddingService embeddings,
      RecordVectorStore vectors,
      PiiRedactor redactor,
      AuditService auditService,
      StringRedisTemplate redis) {
    this.records = records;
    this.recordService = recordService;
    this.consentService = consentService;
    this.keyService = keyService;
    this.embeddings = embeddings;
    this.vectors = vectors;
    this.redactor = redactor;
    this.auditService = auditService;
    this.redis = redis;
  }

  @Override
  @Transactional(readOnly = true)
  public RecallResponse recall(
      UUID customerId,
      String question,
      int topK,
      List<String> kinds,
      boolean strict,
      String actor) {

    String pseudoId = keyService.pseudoId(customerId);
    rateLimit(pseudoId);

    if (!consentService.allowsRecords(pseudoId)) {
      // Silent degradation is deliberate: an agent asking about memory should not blow up because
      // the customer never opted in. `strict` is for callers that really do need to know.
      if (strict) {
        throw new BusinessException(ErrorCode.NO_CONSENT);
      }
      auditService.record(pseudoId, actor, "memory:read", OP_RECALL, 0);
      return new RecallResponse(new ConsentFlags(false), List.of(), List.of());
    }

    Optional<byte[]> dek = keyService.dekIfPresent(pseudoId);
    if (dek.isEmpty()) {
      auditService.record(pseudoId, actor, "memory:read", OP_RECALL, 0);
      return new RecallResponse(new ConsentFlags(true), List.of(), List.of());
    }

    // The caller is supposed to have redacted already; doing it again is cheap and this is the
    // last boundary before the text reaches an embedding model.
    String safeQuestion = redactor.redact(question);

    List<MemoryRecordDto> pinned =
        records.findPinned(pseudoId, Instant.now()).stream()
            .map(record -> recordService.toDto(record, dek.get()))
            .toList();

    int limit = Math.min(topK <= 0 ? MemoryConstants.RECALL_TOP_K : topK,
        MemoryConstants.RECALL_TOP_K_MAX);
    List<ScoredRecord> matches = new ArrayList<>();
    for (RecordVectorStore.Hit hit :
        vectors.search(pseudoId, embeddings.embed(safeQuestion), limit)) {
      Optional<MemoryRecord> record = records.findByIdAndPseudoId(hit.recordId(), pseudoId);
      if (record.isEmpty()) {
        continue;
      }
      if (!MemoryConstants.STATES_RECALLABLE.contains(record.get().getState())) {
        continue;
      }
      if (kinds != null && !kinds.isEmpty() && !kinds.contains(record.get().getKind())) {
        continue;
      }
      matches.add(new ScoredRecord(recordService.toDto(record.get(), dek.get()), hit.score()));
    }

    auditService.record(pseudoId, actor, "memory:read", OP_RECALL, matches.size() + pinned.size());
    return new RecallResponse(new ConsentFlags(true), pinned, matches);
  }

  /** Per-customer, not per-caller: it caps how fast anyone can drain one customer's memory. */
  private void rateLimit(String pseudoId) {
    String key = RATE_KEY + pseudoId;
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redis.expire(key, Duration.ofMinutes(1));
    }
    if (count != null && count > MemoryConstants.RECALL_RATE_PER_MINUTE) {
      throw new BusinessException(
          ErrorCode.MEMORY_RATE_LIMITED,
          Map.of("limit", MemoryConstants.RECALL_RATE_PER_MINUTE, "windowSeconds", 60));
    }
  }
}
