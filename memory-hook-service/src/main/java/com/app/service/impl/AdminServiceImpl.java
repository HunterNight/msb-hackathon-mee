package com.app.service.impl;

import com.app.constant.ErrorCode;
import com.app.constant.MemoryConstants;
import com.app.constant.Topics;
import com.app.dto.request.MemoryRequests.KeyRotationRequest;
import com.app.dto.request.MemoryRequests.ReplayRequest;
import com.app.dto.response.MemoryResponses.Anomaly;
import com.app.dto.response.MemoryResponses.DlqRow;
import com.app.dto.response.MemoryResponses.KeyRotationResponse;
import com.app.dto.response.MemoryResponses.PoisoningMetrics;
import com.app.dto.response.MemoryResponses.ReplayResponse;
import com.app.exception.BusinessException;
import com.app.model.DlqEntry;
import com.app.repository.DlqEntryRepository;
import com.app.repository.IngestLogRepository;
import com.app.service.AdminService;
import com.app.service.IngestService;
import com.app.service.crypto.KeyService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminServiceImpl implements AdminService {

  private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);
  private static final Duration POLL = Duration.ofSeconds(2);
  private static final int MAX_EMPTY_POLLS = 3;

  private final DlqEntryRepository dlq;
  private final IngestLogRepository ingestLog;
  private final IngestService ingestService;
  private final ConsumerFactory<String, String> consumerFactory;
  private final KeyService keyService;

  public AdminServiceImpl(
      DlqEntryRepository dlq,
      IngestLogRepository ingestLog,
      IngestService ingestService,
      ConsumerFactory<String, String> consumerFactory,
      KeyService keyService) {
    this.dlq = dlq;
    this.ingestLog = ingestLog;
    this.ingestService = ingestService;
    this.consumerFactory = consumerFactory;
    this.keyService = keyService;
  }

  /**
   * Reads the requested window straight out of Kafka retention with a throwaway group, so the live
   * consumer group's offsets are untouched. Consent is re-evaluated by the ingest pipeline, which
   * is the whole reason replay goes through it rather than writing rows directly.
   */
  @Override
  public ReplayResponse replay(ReplayRequest request) {
    String pseudoId =
        request.pseudoId() != null
            ? request.pseudoId()
            : request.customerId() == null ? null : keyService.pseudoId(request.customerId());

    List<String> topics =
        request.topics() == null || request.topics().isEmpty()
            ? List.of(Topics.ALL)
            : request.topics();

    int replayed = 0;
    try (Consumer<String, String> consumer =
        consumerFactory.createConsumer("memory-replay-" + UUID.randomUUID(), null)) {

      List<TopicPartition> partitions = new ArrayList<>();
      Map<TopicPartition, Long> seekTargets = new HashMap<>();
      for (String topic : topics) {
        List<PartitionInfo> infos = consumer.partitionsFor(topic);
        if (infos == null) {
          continue;
        }
        for (PartitionInfo info : infos) {
          TopicPartition partition = new TopicPartition(topic, info.partition());
          partitions.add(partition);
          seekTargets.put(partition, request.from().toEpochMilli());
        }
      }
      if (partitions.isEmpty()) {
        return new ReplayResponse(UUID.randomUUID(), 0);
      }
      consumer.assign(partitions);
      consumer
          .offsetsForTimes(seekTargets)
          .forEach(
              (partition, offset) -> {
                if (offset == null) {
                  consumer.seekToEnd(List.of(partition));
                } else {
                  consumer.seek(partition, offset.offset());
                }
              });

      int emptyPolls = 0;
      while (emptyPolls < MAX_EMPTY_POLLS) {
        ConsumerRecords<String, String> batch = consumer.poll(POLL);
        if (batch.isEmpty()) {
          emptyPolls++;
          continue;
        }
        emptyPolls = 0;
        for (ConsumerRecord<String, String> record : batch) {
          if (record.timestamp() > request.to().toEpochMilli()) {
            continue;
          }
          String eventId = header(record, "ce_id");
          if (eventId == null) {
            continue;
          }
          if (pseudoId != null && !matches(record, pseudoId)) {
            continue;
          }
          // The pipeline is idempotent on ce_id, so the log entry has to go for a real re-run.
          ingestLog.deleteById(eventId);
          ingestService.consume(
              eventId,
              header(record, "ce_type"),
              header(record, "ce_source"),
              header(record, "ce_subject"),
              record.value());
          replayed++;
        }
      }
    } catch (Exception e) {
      log.warn("replay aborted after {} events", replayed);
      throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
    }
    return new ReplayResponse(UUID.randomUUID(), replayed);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<DlqRow> dlq(Pageable pageable) {
    return dlq.findAllByOrderByAtDesc(pageable)
        .map(
            entry ->
                new DlqRow(
                    entry.getId(),
                    entry.getEventId(),
                    entry.getEventType(),
                    entry.getReason(),
                    entry.getAttempts(),
                    entry.getAt()));
  }

  @Override
  @Transactional
  public String retry(UUID id) {
    DlqEntry entry =
        dlq.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.MEMORY_NOT_FOUND));
    entry.setAttempts(entry.getAttempts() + 1);
    dlq.save(entry);
    // The stored body is redacted, so a retry can only succeed for envelope-level failures; a
    // payload-level rejection needs a replay from Kafka instead.
    ingestLog.deleteById(entry.getEventId());
    ingestService.consume(
        entry.getEventId(),
        entry.getEventType(),
        MemoryConstants.ALLOWED_SOURCES.get(0),
        null,
        entry.getRedactedBody());
    dlq.delete(entry);
    return "RETRIED";
  }

  @Override
  @Transactional
  public void discard(UUID id) {
    DlqEntry entry =
        dlq.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.MEMORY_NOT_FOUND));
    dlq.delete(entry);
    log.info("dlq entry {} discarded by ops", entry.getEventId());
  }

  @Override
  @Transactional(readOnly = true)
  public PoisoningMetrics poisoning(Instant from, Instant to) {
    long rejected = ingestLog.countByStatusAndAtBetween(MemoryConstants.INGEST_REJECTED, from, to);
    long dropped =
        ingestLog.countByStatusAndAtBetween(MemoryConstants.INGEST_DROPPED_SENSITIVE, from, to);
    long dlqCount = dlq.countByAtBetween(from, to);

    List<Anomaly> anomalies =
        dlq.findAllByOrderByAtDesc(Pageable.ofSize(20)).getContent().stream()
            .filter(entry -> !entry.getAt().isBefore(from) && !entry.getAt().isAfter(to))
            .map(entry -> new Anomaly(entry.getEventId(), entry.getReason(), entry.getAt()))
            .toList();

    return new PoisoningMetrics(
        anomalies.size(),
        Map.of(
            MemoryConstants.REJECT_PII, rejected,
            MemoryConstants.REJECT_SENSITIVE, dropped,
            MemoryConstants.REJECT_SCHEMA, dlqCount),
        0L,
        anomalies);
  }

  @Override
  public KeyRotationResponse rotate(KeyRotationRequest request) {
    KeyService.KeyRotationResult result = keyService.rotate(request.kekVersion());
    return new KeyRotationResponse(result.rewrapped(), result.failed());
  }

  private boolean matches(ConsumerRecord<String, String> record, String pseudoId) {
    String subject = header(record, "ce_subject");
    if (subject == null) {
      return false;
    }
    try {
      return keyService.pseudoId(UUID.fromString(subject)).equals(pseudoId);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private String header(ConsumerRecord<String, String> record, String name) {
    var header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }
}
