package com.app.service.impl;

import com.app.constant.ErrorCode;
import com.app.constant.MemoryConstants;
import com.app.dto.response.MemoryResponses.ErasureProof;
import com.app.dto.response.MemoryResponses.ErasureResponse;
import com.app.dto.request.MemoryRequests.ErasureRequestBody;
import com.app.exception.BusinessException;
import com.app.model.ErasureRequest;
import com.app.repository.AccessAuditRepository;
import com.app.repository.CustomerSnapshotRepository;
import com.app.repository.ErasureRequestRepository;
import com.app.repository.EventFactRepository;
import com.app.repository.MemoryRecordRepository;
import com.app.repository.RecordSourceRepository;
import com.app.service.AuditService;
import com.app.service.ErasureService;
import com.app.service.RecordVectorStore;
import com.app.service.crypto.KeyService;
import com.app.service.publisher.MemoryUpdatedPublisher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Erasure is crypto-shredding plus row deletion plus a tombstone, in that order, and it produces a
 * proof the customer can be shown (design §4 "Erasure").
 */
@Service
public class ErasureServiceImpl implements ErasureService {

  private static final Logger log = LoggerFactory.getLogger(ErasureServiceImpl.class);
  private static final String OP_ERASE = "ERASE";

  private final ErasureRequestRepository erasures;
  private final MemoryRecordRepository records;
  private final RecordSourceRepository sources;
  private final CustomerSnapshotRepository snapshots;
  private final EventFactRepository facts;
  private final AccessAuditRepository audits;
  private final RecordVectorStore vectors;
  private final KeyService keyService;
  private final MemoryUpdatedPublisher publisher;
  private final AuditService auditService;
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public ErasureServiceImpl(
      ErasureRequestRepository erasures,
      MemoryRecordRepository records,
      RecordSourceRepository sources,
      CustomerSnapshotRepository snapshots,
      EventFactRepository facts,
      AccessAuditRepository audits,
      RecordVectorStore vectors,
      KeyService keyService,
      MemoryUpdatedPublisher publisher,
      AuditService auditService,
      StringRedisTemplate redis,
      ObjectMapper objectMapper) {
    this.erasures = erasures;
    this.records = records;
    this.sources = sources;
    this.snapshots = snapshots;
    this.facts = facts;
    this.audits = audits;
    this.vectors = vectors;
    this.keyService = keyService;
    this.publisher = publisher;
    this.auditService = auditService;
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public ErasureResponse request(UUID customerId, ErasureRequestBody body, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    if (inProgress(pseudoId)) {
      throw new BusinessException(ErrorCode.ERASURE_IN_PROGRESS);
    }
    ErasureRequest request = queue(pseudoId, body.reason(), body.requestedBy());
    auditService.record(pseudoId, actor, "memory:erase", OP_ERASE, 0);
    return toDto(request);
  }

  @Override
  @Transactional(readOnly = true)
  public ErasureResponse status(UUID customerId, UUID erasureId) {
    String pseudoId = keyService.pseudoId(customerId);
    return erasures
        .findByIdAndPseudoId(erasureId, pseudoId)
        .map(this::toDto)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEMORY_NOT_FOUND));
  }

  @Override
  @Transactional
  public void queueForConsentWithdrawal(String pseudoId, String actor) {
    if (inProgress(pseudoId)) {
      return;
    }
    queue(pseudoId, "CONSENT_WITHDRAWN", actor);
  }

  @Override
  @Transactional
  public void execute(UUID erasureId) {
    ErasureRequest request = erasures.findById(erasureId).orElse(null);
    if (request == null || MemoryConstants.ERASURE_DONE.equals(request.getStatus())) {
      return;
    }
    request.setStatus(MemoryConstants.ERASURE_RUNNING);
    erasures.save(request);

    String pseudoId = request.getPseudoId();
    int vectorCount = vectors.countFor(pseudoId);
    List<com.app.model.MemoryRecord> owned = records.findByPseudoId(pseudoId);
    owned.forEach(record -> sources.deleteAll(sources.findByRecordId(record.getId())));
    // The bulk delete below does not touch record_source, so Hibernate would not flush these queued
    // deletes ahead of it. The FK's ON DELETE CASCADE then removes the rows first and the queued
    // deletes find nothing at commit — a StaleStateException that rolled the whole erasure back on
    // every 2-minute tick and left the customer's request stuck.
    sources.flush();
    int recordCount = records.deleteByPseudoId(pseudoId);
    boolean hadSnapshot = snapshots.findById(pseudoId).isPresent();
    snapshots.findById(pseudoId).ifPresent(snapshots::delete);
    facts.deleteByPseudoId(pseudoId);

    // Caches that could still answer a recall have to go before the tombstone is published.
    redis.delete("memory:recall:" + pseudoId);

    // Crypto-shredding last among the deletes: with the DEK gone, any leftover ciphertext in a
    // backup is already unreadable.
    keyService.destroy(pseudoId);
    long offset = publisher.tombstone(pseudoId);

    // The audit trail is kept (it holds no customer content) but is stripped of this namespace
    // once the proof has been computed, so the pseudonym itself stops being linkable.
    long audited = audits.countByPseudoIdAndOperationAndAtAfter(pseudoId, OP_ERASE, Instant.EPOCH);

    ErasureProof proof =
        new ErasureProof(
            recordCount,
            vectorCount,
            hadSnapshot,
            true,
            offset,
            hash(pseudoId, recordCount, vectorCount, offset));
    request.setStatus(MemoryConstants.ERASURE_DONE);
    request.setCompletedAt(Instant.now());
    request.setProof(objectMapper.writeValueAsString(proof));
    erasures.save(request);
    log.info(
        "erasure completed pseudo={} records={} vectors={} auditRows={}",
        pseudoId.substring(0, 12),
        recordCount,
        vectorCount,
        audited);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean inProgress(String pseudoId) {
    return erasures
        .existsByPseudoIdAndStatusIn(
            pseudoId, List.of(MemoryConstants.ERASURE_QUEUED, MemoryConstants.ERASURE_RUNNING));
  }

  private ErasureRequest queue(String pseudoId, String reason, String requestedBy) {
    ErasureRequest request = new ErasureRequest();
    request.setPseudoId(pseudoId);
    request.setStatus(MemoryConstants.ERASURE_QUEUED);
    request.setReason(reason);
    request.setRequestedBy(requestedBy);
    request.setRequestedAt(Instant.now());
    request.setSlaAt(Instant.now().plus(MemoryConstants.ERASURE_SLA));
    return erasures.save(request);
  }

  private ErasureResponse toDto(ErasureRequest request) {
    ErasureProof proof =
        request.getProof() == null
            ? null
            : objectMapper.readValue(request.getProof(), ErasureProof.class);
    return new ErasureResponse(
        request.getId(),
        request.getStatus(),
        request.getRequestedAt(),
        request.getSlaAt(),
        request.getCompletedAt(),
        proof);
  }

  private String hash(String pseudoId, int records, int vectors, long offset) {
    try {
      String material = pseudoId + "|" + records + "|" + vectors + "|" + offset;
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.INTERNAL);
    }
  }
}
