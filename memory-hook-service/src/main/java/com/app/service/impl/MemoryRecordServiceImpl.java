package com.app.service.impl;

import com.app.constant.ErrorCode;
import com.app.constant.MemoryConstants;
import com.app.dto.request.MemoryRequests.CandidateRequest;
import com.app.dto.request.MemoryRequests.EditRequest;
import com.app.dto.request.MemoryRequests.RememberRequest;
import com.app.dto.response.MemoryResponses.MemoryRecordDto;
import com.app.exception.BusinessException;
import com.app.model.MemoryLink;
import com.app.model.MemoryRecord;
import com.app.model.RecordSource;
import com.app.repository.MemoryLinkRepository;
import com.app.repository.MemoryRecordRepository;
import com.app.repository.RecordSourceRepository;
import com.app.service.AuditService;
import com.app.service.ConsentService;
import com.app.service.EmbeddingService;
import com.app.service.ErasureService;
import com.app.service.MemoryRecordService;
import com.app.service.RecordVectorStore;
import com.app.service.crypto.EnvelopeCipher;
import com.app.service.crypto.KeyService;
import com.app.service.privacy.DlpValidator;
import com.app.service.privacy.SensitiveClassifier;
import com.app.service.publisher.MemoryUpdatedPublisher;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class MemoryRecordServiceImpl implements MemoryRecordService {

  private static final String OP_LIST = "RECORDS_LIST";
  private static final String OP_WRITE = "RECORD_WRITE";
  private static final String OP_DELETE = "RECORD_DELETE";
  private static final String OP_CONFIRM = "RECORD_CONFIRM";
  private static final String OP_REJECT = "RECORD_REJECT";
  private static final String OP_EDIT = "RECORD_EDIT";
  private static final String OP_DEACTIVATE = "RECORD_DEACTIVATE";
  private static final String OP_FORGET = "RECORD_FORGET";
  private static final String OP_HYPOTHESIS = "HYPOTHESIS_NEXT";
  private static final String OP_WHY = "RECORD_WHY";
  private static final String OP_CANDIDATE = "CANDIDATE_SUBMIT";

  private final MemoryRecordRepository records;
  private final MemoryLinkRepository links;
  private final RecordSourceRepository sources;
  private final KeyService keyService;
  private final EnvelopeCipher cipher;
  private final ConsentService consentService;
  private final ErasureService erasureService;
  private final DlpValidator dlp;
  private final SensitiveClassifier classifier;
  private final EmbeddingService embeddings;
  private final RecordVectorStore vectors;
  private final MemoryUpdatedPublisher publisher;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public MemoryRecordServiceImpl(
      MemoryRecordRepository records,
      MemoryLinkRepository links,
      RecordSourceRepository sources,
      KeyService keyService,
      EnvelopeCipher cipher,
      ConsentService consentService,
      ErasureService erasureService,
      DlpValidator dlp,
      SensitiveClassifier classifier,
      EmbeddingService embeddings,
      RecordVectorStore vectors,
      MemoryUpdatedPublisher publisher,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.records = records;
    this.links = links;
    this.sources = sources;
    this.keyService = keyService;
    this.cipher = cipher;
    this.consentService = consentService;
    this.erasureService = erasureService;
    this.dlp = dlp;
    this.classifier = classifier;
    this.embeddings = embeddings;
    this.vectors = vectors;
    this.publisher = publisher;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public MemoryRecordDto remember(UUID customerId, RememberRequest request, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    if (!consentService.allowsRecords(pseudoId)) {
      throw new BusinessException(ErrorCode.NO_CONSENT);
    }
    if (erasureService.inProgress(pseudoId)) {
      throw new BusinessException(ErrorCode.ERASURE_IN_PROGRESS);
    }
    // Chat is the least trusted source, so it is the one with a daily cap (design §4 "Poisoning").
    long today =
        records.countByPseudoIdAndSourceAndCreatedAtAfter(
            pseudoId, MemoryConstants.SOURCE_CHAT, Instant.now().truncatedTo(ChronoUnit.DAYS));
    if (today >= MemoryConstants.CHAT_RECORDS_PER_DAY) {
      throw new BusinessException(
          ErrorCode.MEMORY_RATE_LIMITED, Map.of("limit", MemoryConstants.CHAT_RECORDS_PER_DAY));
    }

    byte[] dek = keyService.dek(customerId);
    MemoryRecord saved =
        upsert(
                new Candidate(
                    pseudoId,
                    dek,
                    request.kind(),
                    request.text(),
                    normalisedKey(request.kind(), request.text(), request.refs()),
                    request.confidenceOrDefault(),
                    MemoryConstants.SOURCE_CHAT,
                    request.refs(),
                    request.messageId().toString(),
                    "chat.remember",
                    Instant.now()))
            // The DLP gate already threw with the precise reason; anything else is a schema fault.
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.RECORD_REJECTED,
                        Map.of("reason", MemoryConstants.REJECT_SCHEMA)));
    auditService.record(pseudoId, actor, "memory:write:chat", OP_WRITE, 1);
    return toDto(saved, dek);
  }

  @Override
  @Transactional
  public Optional<MemoryRecord> upsert(Candidate candidate) {
    if (candidate.confidence().compareTo(MemoryConstants.CONFIDENCE_MIN) < 0) {
      return Optional.empty();
    }
    String abstractText;
    try {
      abstractText = dlp.validateAndAbstract(candidate.kind(), candidate.text());
    } catch (BusinessException e) {
      // A chat write wants the reason; an extractor just drops the candidate.
      if (MemoryConstants.SOURCE_CHAT.equals(candidate.source())) {
        throw e;
      }
      return Optional.empty();
    }
    String classification = classifier.classify(candidate.text(), candidate.kind());
    if (MemoryConstants.CLASS_SENSITIVE.equals(classification)) {
      return Optional.empty();
    }
    if (MemoryConstants.KINDS_NOT_STORED.contains(candidate.kind())) {
      return Optional.empty();
    }

    Optional<MemoryRecord> existing =
        records.findByPseudoIdAndKindAndNormalisedKeyAndSupersedesIsNull(
            candidate.pseudoId(), candidate.kind(), candidate.normalisedKey());

    MemoryRecord record = new MemoryRecord();
    record.setPseudoId(candidate.pseudoId());
    record.setKind(candidate.kind());
    record.setNormalisedKey(candidate.normalisedKey());
    record.setAbstractText(abstractText);
    record.setTextEnc(encrypt(candidate.dek(), candidate.text()));
    record.setConfidence(candidate.confidence());
    record.setSource(candidate.source());
    record.setClassification(classification);
    record.setValidFrom(Instant.now());
    record.setValidUntil(Instant.now().plus(MemoryConstants.ttlFor(candidate.kind())));
    record.setPersistence(MemoryConstants.persistenceFor(candidate.kind()));
    record.setState(initialState(candidate.source()));
    record.setExplicit(MemoryConstants.SOURCE_CHAT.equals(candidate.source()));
    if (candidate.refs() != null && !candidate.refs().isEmpty()) {
      record.setRefsEnc(
          encrypt(candidate.dek(), objectMapper.writeValueAsString(candidate.refs())));
    }

    if (existing.isPresent()) {
      MemoryRecord previous = existing.get();
      // Conflicting facts within a day: the more confident one wins rather than the newer one.
      boolean fresh = previous.getCreatedAt().isBefore(Instant.now().minus(1, ChronoUnit.DAYS));
      if (!fresh && previous.getConfidence().compareTo(candidate.confidence()) > 0) {
        return Optional.of(previous);
      }
      previous.setSupersedes(record.getId());
      previous.setValidUntil(Instant.now());
      records.save(previous);
      vectors.upsert(previous.getId(), new float[MemoryConstants.EMBEDDING_DIM]);
    }

    // Flushed for the same reason as the chunk index: the embedding is written with native SQL.
    MemoryRecord saved = records.saveAndFlush(record);
    vectors.upsert(saved.getId(), embeddings.embed(abstractText));

    RecordSource source = new RecordSource();
    source.setRecordId(saved.getId());
    source.setEventId(candidate.eventId());
    source.setEventType(candidate.eventType());
    source.setEventTime(candidate.eventTime());
    sources.save(source);

    publisher.published(candidate.pseudoId(), List.of(candidate.kind()), 1);
    return Optional.of(saved);
  }

  private String initialState(String source) {
    return switch (source) {
      case MemoryConstants.SOURCE_CHAT -> MemoryConstants.STATE_CANDIDATE;
      case MemoryConstants.SOURCE_EVENTS -> MemoryConstants.STATE_HYPOTHESIS;
      default -> MemoryConstants.STATE_HYPOTHESIS;
    };
  }

  @Override
  @Transactional(readOnly = true)
  public Page<MemoryRecordDto> list(UUID customerId, String kind, Pageable pageable) {
    String pseudoId = keyService.pseudoId(customerId);
    Optional<byte[]> dek = keyService.dekIfPresent(pseudoId);
    if (dek.isEmpty()) {
      return Page.empty(pageable);
    }
    Page<MemoryRecord> page = records.findLive(pseudoId, kind, Instant.now(), pageable);
    auditService.record(pseudoId, actor(), "memory:read", OP_LIST, page.getNumberOfElements());
    return page.map(record -> toDto(record, dek.get()));
  }

  @Override
  @Transactional
  public void delete(UUID customerId, UUID recordId, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    MemoryRecord record =
        records
            .findByIdAndPseudoId(recordId, pseudoId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMORY_NOT_FOUND));
    sources.deleteAll(sources.findByRecordId(record.getId()));
    records.delete(record);
    publisher.published(pseudoId, List.of(record.getKind()), 1);
    auditService.record(pseudoId, actor, "memory:erase", OP_DELETE, 1);
  }

  @Override
  @Transactional
  public CandidateDecision submitCandidate(UUID customerId, CandidateRequest request, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    if (!consentService.allowsRecords(pseudoId)) {
      throw new BusinessException(ErrorCode.NO_CONSENT);
    }
    if (erasureService.inProgress(pseudoId)) {
      throw new BusinessException(ErrorCode.ERASURE_IN_PROGRESS);
    }

    if (MemoryConstants.KINDS_NOT_STORED.contains(request.kind())) {
      auditService.record(pseudoId, actor, "memory:write:chat", OP_CANDIDATE, 0);
      return new CandidateDecision("DONT_STORE", null);
    }

    byte[] dek = keyService.dek(customerId);
    String normalisedKey = normalisedKey(request.kind(), request.text(), request.refs());

    Optional<MemoryRecord> existing =
        records.findByPseudoIdAndKindAndNormalisedKeyAndSupersedesIsNull(
            pseudoId, request.kind(), normalisedKey);
    if (existing.isPresent() && MemoryConstants.STATE_CONFIRMED.equals(existing.get().getState())) {
      auditService.record(pseudoId, actor, "memory:write:chat", OP_CANDIDATE, 0);
      return new CandidateDecision("UPDATE_OF:" + existing.get().getId(), toDto(existing.get(), dek));
    }

    MemoryRecord saved =
        upsert(
                new Candidate(
                    pseudoId,
                    dek,
                    request.kind(),
                    request.text(),
                    normalisedKey,
                    request.confidenceOrDefault(),
                    MemoryConstants.SOURCE_CHAT,
                    request.refs(),
                    request.messageId().toString(),
                    "chat.candidate",
                    Instant.now()))
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.RECORD_REJECTED,
                        Map.of("reason", MemoryConstants.REJECT_SCHEMA)));
    if (request.entity() != null) {
      saved.setEntity(request.entity());
    }
    if (request.reason() != null && !request.reason().isBlank()) {
      saved.setReasonEnc(encrypt(dek, request.reason()));
    }
    records.save(saved);
    auditService.record(pseudoId, actor, "memory:write:chat", OP_CANDIDATE, 1);
    return new CandidateDecision("CANDIDATE", toDto(saved, dek));
  }

  @Override
  @Transactional
  public MemoryRecordDto confirm(UUID customerId, UUID recordId, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    MemoryRecord record = loadOwned(recordId, pseudoId);
    String state = record.getState();
    if (!MemoryConstants.STATE_CANDIDATE.equals(state)
        && !MemoryConstants.STATE_HYPOTHESIS.equals(state)
        && !MemoryConstants.STATE_EXPIRED.equals(state)) {
      throw new BusinessException(ErrorCode.MEMORY_NOT_FOUND);
    }
    record.setState(MemoryConstants.STATE_CONFIRMED);
    record.setCustomerConfirmed(true);
    record.setLastConfirmedAt(Instant.now());
    MemoryRecord saved = records.save(record);
    auditService.record(pseudoId, actor, "memory:write:chat", OP_CONFIRM, 1);
    return toDto(saved, keyService.dek(customerId));
  }

  @Override
  @Transactional
  public MemoryRecordDto reject(UUID customerId, UUID recordId, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    MemoryRecord record = loadOwned(recordId, pseudoId);
    record.setState(MemoryConstants.STATE_REJECTED);
    record.setSuppressedUntil(Instant.now().plus(MemoryConstants.SUPPRESSION_AFTER_REJECT));
    MemoryRecord saved = records.save(record);
    vectors.upsert(saved.getId(), new float[MemoryConstants.EMBEDDING_DIM]);
    auditService.record(pseudoId, actor, "memory:write:chat", OP_REJECT, 1);
    return toDto(saved, keyService.dek(customerId));
  }

  @Override
  @Transactional
  public MemoryRecordDto edit(UUID customerId, UUID recordId, EditRequest request, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    MemoryRecord record = loadOwned(recordId, pseudoId);
    byte[] dek = keyService.dek(customerId);
    String abstractText = dlp.validateAndAbstract(record.getKind(), request.text());
    record.setAbstractText(abstractText);
    record.setTextEnc(encrypt(dek, request.text()));
    if (request.reason() != null && !request.reason().isBlank()) {
      record.setReasonEnc(encrypt(dek, request.reason()));
    }
    record.setCustomerConfirmed(true);
    record.setLastConfirmedAt(Instant.now());
    record.setState(MemoryConstants.STATE_CONFIRMED);
    MemoryRecord saved = records.save(record);
    vectors.upsert(saved.getId(), embeddings.embed(abstractText));
    auditService.record(pseudoId, actor, "memory:write:chat", OP_EDIT, 1);
    return toDto(saved, dek);
  }

  @Override
  @Transactional
  public MemoryRecordDto deactivate(UUID customerId, UUID recordId, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    MemoryRecord record = loadOwned(recordId, pseudoId);
    record.setState(MemoryConstants.STATE_INACTIVE);
    record.setValidUntil(Instant.now());
    MemoryRecord saved = records.save(record);
    auditService.record(pseudoId, actor, "memory:write:chat", OP_DEACTIVATE, 1);
    return toDto(saved, keyService.dek(customerId));
  }

  @Override
  @Transactional
  public void forget(UUID customerId, UUID recordId, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    MemoryRecord record = loadOwned(recordId, pseudoId);
    record.setState(MemoryConstants.STATE_FORGOTTEN);
    record.setTextEnc(new byte[0]);
    record.setReasonEnc(null);
    record.setRefsEnc(null);
    record.setValidUntil(Instant.now());
    MemoryRecord saved = records.save(record);
    vectors.upsert(saved.getId(), new float[MemoryConstants.EMBEDDING_DIM]);
    auditService.record(pseudoId, actor, "memory:erase", OP_FORGET, 1);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MemoryRecordDto> nextHypothesis(UUID customerId, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    Optional<byte[]> dek = keyService.dekIfPresent(pseudoId);
    if (dek.isEmpty()) {
      return Optional.empty();
    }
    List<MemoryRecord> hypotheses = records.findHypotheses(pseudoId, Instant.now());
    if (hypotheses.isEmpty()) {
      return Optional.empty();
    }
    auditService.record(pseudoId, actor, "memory:read", OP_HYPOTHESIS, 1);
    return Optional.of(toDto(hypotheses.getFirst(), dek.get()));
  }

  @Override
  @Transactional(readOnly = true)
  public String why(UUID customerId, UUID recordId, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    MemoryRecord record = loadOwned(recordId, pseudoId);
    auditService.record(pseudoId, actor, "memory:read", OP_WHY, 1);
    if (record.getEvidenceSummary() != null) {
      return record.getEvidenceSummary();
    }
    return record.getAbstractText();
  }

  private MemoryRecord loadOwned(UUID recordId, String pseudoId) {
    return records
        .findByIdAndPseudoId(recordId, pseudoId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEMORY_NOT_FOUND));
  }

  @Override
  @Transactional(readOnly = true)
  public com.app.dto.response.MemoryResponses.MemoryGraphResponse graph(
      UUID customerId, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    Optional<byte[]> dek = keyService.dekIfPresent(pseudoId);
    if (dek.isEmpty()) {
      return new com.app.dto.response.MemoryResponses.MemoryGraphResponse(List.of(), List.of());
    }
    List<MemoryRecord> nodes = records.findGraphNodes(pseudoId, Instant.now());
    List<UUID> nodeIds = nodes.stream().map(MemoryRecord::getId).toList();
    List<MemoryLink> graphLinks =
        nodeIds.isEmpty() ? List.of() : links.findGraphLinks(pseudoId, nodeIds);
    List<com.app.dto.response.MemoryResponses.MemoryLinkDto> linkDtos =
        graphLinks.stream()
            .map(
                l ->
                    new com.app.dto.response.MemoryResponses.MemoryLinkDto(
                        l.getId(),
                        l.getFromRecord(),
                        l.getToRecord(),
                        l.getRelation(),
                        l.getState(),
                        l.getCreatedAt(),
                        l.getConfirmedAt()))
            .toList();
    auditService.record(pseudoId, actor, "memory:read", "GRAPH", nodes.size());
    return new com.app.dto.response.MemoryResponses.MemoryGraphResponse(
        nodes.stream().map(n -> toDto(n, dek.get())).toList(), linkDtos);
  }

  @Override
  public MemoryRecordDto toDto(MemoryRecord record, byte[] dek) {
    Map<String, String> refs = Map.of();
    if (record.getRefsEnc() != null && record.getRefsEnc().length > 0) {
      refs =
          objectMapper.readValue(
              decrypt(dek, record.getRefsEnc()), new TypeReference<Map<String, String>>() {});
    }
    return new MemoryRecordDto(
        record.getId(),
        record.getKind(),
        // Only the abstract form ever leaves the service (design §4 "Access").
        record.getAbstractText(),
        record.getConfidence(),
        record.isPinned(),
        record.getSource(),
        record.getValidUntil() == null
            ? null
            : LocalDate.ofInstant(record.getValidUntil(), com.app.constant.AppConstants.USER_ZONE),
        record.getCreatedAt(),
        refs,
        record.getState(),
        record.isExplicit(),
        record.isCustomerConfirmed(),
        record.getLastConfirmedAt(),
        record.getPersistence(),
        record.getEntity(),
        record.getEvidenceSummary());
  }

  @Override
  @Transactional
  public int expire(Instant now, int batchSize) {
    List<MemoryRecord> expired = records.findExpired(now, PageRequest.of(0, batchSize));
    for (MemoryRecord record : expired) {
      if (MemoryConstants.STATE_CONFIRMED.equals(record.getState())) {
        record.setState(MemoryConstants.STATE_EXPIRED);
        records.save(record);
      } else {
        sources.deleteAll(sources.findByRecordId(record.getId()));
        records.delete(record);
      }
    }
    return expired.size();
  }

  /** The identity of a fact within its kind: a ref id when there is one, else the folded text. */
  private String normalisedKey(String kind, String text, Map<String, String> refs) {
    if (refs != null) {
      for (String key : List.of("beneficiaryId", "goalId", "triggerCode")) {
        if (refs.get(key) != null) {
          return kind + ":" + refs.get(key);
        }
      }
    }
    String folded =
        java.text.Normalizer.normalize(text.toLowerCase(java.util.Locale.ROOT),
                java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9]+", "-");
    return kind + ":" + folded.substring(0, Math.min(48, folded.length()));
  }

  private byte[] encrypt(byte[] dek, String value) {
    return cipher.encrypt(dek, value);
  }

  private String decrypt(byte[] dek, byte[] value) {
    return cipher.decrypt(dek, value);
  }

  private String actor() {
    var authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    return authentication == null ? "system" : authentication.getName();
  }
}
