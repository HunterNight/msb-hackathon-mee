package com.app.service.impl;

import com.app.constant.MemoryConstants;
import com.app.model.DlqEntry;
import com.app.model.IngestLog;
import com.app.repository.DlqEntryRepository;
import com.app.repository.IngestLogRepository;
import com.app.service.ConsentService;
import com.app.service.IngestService;
import com.app.service.MemoryRecordService;
import com.app.service.SnapshotService;
import com.app.service.consumer.FactDistiller;
import com.app.service.crypto.KeyService;
import com.app.service.extract.ExtractionContext;
import com.app.service.extract.MemoryExtractor;
import com.app.service.privacy.PiiRedactor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Order matters: validate, allow-list, dedupe, then consent, and only then anything that writes
 * memory (design §1).
 */
@Service
public class IngestServiceImpl implements IngestService {

  private static final Logger log = LoggerFactory.getLogger(IngestServiceImpl.class);

  private final IngestLogRepository ingestLog;
  private final DlqEntryRepository dlq;
  private final ConsentService consentService;
  private final KeyService keyService;
  private final FactDistiller distiller;
  private final SnapshotService snapshotService;
  private final MemoryRecordService recordService;
  private final List<MemoryExtractor> extractors;
  private final PiiRedactor redactor;
  private final ObjectMapper objectMapper;

  public IngestServiceImpl(
      IngestLogRepository ingestLog,
      DlqEntryRepository dlq,
      ConsentService consentService,
      KeyService keyService,
      FactDistiller distiller,
      SnapshotService snapshotService,
      MemoryRecordService recordService,
      List<MemoryExtractor> extractors,
      PiiRedactor redactor,
      ObjectMapper objectMapper) {
    this.ingestLog = ingestLog;
    this.dlq = dlq;
    this.consentService = consentService;
    this.keyService = keyService;
    this.distiller = distiller;
    this.snapshotService = snapshotService;
    this.recordService = recordService;
    this.extractors = extractors;
    this.redactor = redactor;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public void consume(
      String eventId, String eventType, String source, String subject, String body) {

    if (eventId == null || eventType == null) {
      reject(eventId, eventType, body, "MISSING_ENVELOPE");
      return;
    }
    if (ingestLog.existsById(eventId)) {
      return;
    }
    if (!MemoryConstants.EVENT_FACTS.containsKey(eventType)) {
      reject(eventId, eventType, body, "UNKNOWN_EVENT_TYPE");
      return;
    }
    if (source == null || !MemoryConstants.ALLOWED_SOURCES.contains(source)) {
      reject(eventId, eventType, body, "SOURCE_NOT_ALLOWED");
      return;
    }

    Map<String, Object> data;
    try {
      data = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      reject(eventId, eventType, body, "SCHEMA");
      return;
    }

    UUID customerId = customerId(data, subject);
    if (customerId == null) {
      reject(eventId, eventType, body, "NO_SUBJECT");
      return;
    }

    String pseudoId = keyService.pseudoId(customerId);
    byte[] dek = keyService.dek(customerId);
    Instant eventTime = Instant.now();

    // Aggregates are allowed on the snapshot consent alone; records need the long-term consent.
    if (consentService.allowsSnapshot(pseudoId)) {
      distiller.distil(pseudoId, eventId, eventType, eventTime, data);
      snapshotService.rebuild(pseudoId, dek);
    }

    if (!consentService.allowsRecords(pseudoId)) {
      write(eventId, eventType, pseudoId, MemoryConstants.INGEST_SKIPPED, null);
      return;
    }

    ExtractionContext context =
        new ExtractionContext(pseudoId, dek, eventId, eventType, eventTime, data);
    int written = 0;
    for (MemoryExtractor extractor : extractors) {
      if (!extractor.supports(eventType)) {
        continue;
      }
      for (MemoryRecordService.Candidate candidate : extractor.extract(context)) {
        written += recordService.upsert(candidate).isPresent() ? 1 : 0;
      }
    }
    write(eventId, eventType, pseudoId, MemoryConstants.INGEST_PROCESSED,
        written == 0 ? "NO_CANDIDATES" : null);
  }

  private UUID customerId(Map<String, Object> data, String subject) {
    Object fromData = data.get("customerId");
    String value = fromData != null ? fromData.toString() : subject;
    try {
      return value == null ? null : UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private void reject(String eventId, String eventType, String body, String reason) {
    DlqEntry entry = new DlqEntry();
    entry.setEventId(eventId == null ? "unknown" : eventId);
    entry.setEventType(eventType == null ? "unknown" : eventType);
    // Redacted, because a poisoned event is exactly the one most likely to carry raw PII.
    entry.setRedactedBody(redactor.redact(body == null ? "" : body));
    entry.setReason(reason);
    dlq.save(entry);
    write(eventId, eventType, null, MemoryConstants.INGEST_REJECTED, reason);
  }

  private void write(
      String eventId, String eventType, String pseudoId, String status, String reason) {
    if (eventId == null) {
      return;
    }
    IngestLog row = new IngestLog();
    row.setEventId(eventId);
    row.setEventType(eventType == null ? "unknown" : eventType);
    row.setPseudoId(pseudoId);
    row.setStatus(status);
    row.setReason(reason);
    ingestLog.save(row);
  }

}
