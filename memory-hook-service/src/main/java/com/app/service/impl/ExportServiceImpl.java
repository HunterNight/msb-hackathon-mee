package com.app.service.impl;

import com.app.constant.ErrorCode;
import com.app.dto.response.MemoryResponses.ExportPayload;
import com.app.dto.response.MemoryResponses.ExportResponse;
import com.app.dto.response.MemoryResponses.MemoryRecordDto;
import com.app.exception.BusinessException;
import com.app.repository.MemoryRecordRepository;
import com.app.service.AuditService;
import com.app.service.ConsentService;
import com.app.service.ExportService;
import com.app.service.MemoryRecordService;
import com.app.service.SnapshotService;
import com.app.service.crypto.KeyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The data-subject export. The document is held in Redis behind a single-use token for five
 * minutes rather than written to disk, so there is no file to forget to delete (design §6).
 */
@Service
public class ExportServiceImpl implements ExportService {

  private static final String OP_EXPORT = "EXPORT";
  private static final String TOKEN_PREFIX = "memory:export:";
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final int MAX_RECORDS = 500;

  private final MemoryRecordRepository records;
  private final MemoryRecordService recordService;
  private final SnapshotService snapshotService;
  private final ConsentService consentService;
  private final KeyService keyService;
  private final AuditService auditService;
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final String baseUrl;

  public ExportServiceImpl(
      MemoryRecordRepository records,
      MemoryRecordService recordService,
      SnapshotService snapshotService,
      ConsentService consentService,
      KeyService keyService,
      AuditService auditService,
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      @Value("${app.memory.export-base-url}") String baseUrl) {
    this.records = records;
    this.recordService = recordService;
    this.snapshotService = snapshotService;
    this.consentService = consentService;
    this.keyService = keyService;
    this.auditService = auditService;
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl;
  }

  @Override
  @Transactional(readOnly = true)
  public ExportResponse export(UUID customerId, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    Optional<byte[]> dek = keyService.dekIfPresent(pseudoId);

    List<MemoryRecordDto> exported =
        dek.map(
                key ->
                    records
                        .findLive(pseudoId, null, Instant.now(), PageRequest.of(0, MAX_RECORDS))
                        .map(record -> recordService.toDto(record, key))
                        .getContent())
            .orElse(List.of());

    ExportPayload payload =
        new ExportPayload(
            exported,
            snapshotService.promptSliceForExport(pseudoId),
            consentService.get(customerId),
            auditService.summarise(pseudoId),
            Instant.now());

    String body = objectMapper.writeValueAsString(payload);
    String token = UUID.randomUUID().toString().replace("-", "");
    redis.opsForValue().set(TOKEN_PREFIX + token, body, TTL);

    auditService.record(pseudoId, actor, "memory:erase", OP_EXPORT, exported.size());
    return new ExportResponse(baseUrl + "/internal/memory/exports/" + token,
        Instant.now().plus(TTL), sha256(body));
  }

  @Override
  public String fetch(String token) {
    String key = TOKEN_PREFIX + token;
    String body = redis.opsForValue().get(key);
    if (body == null) {
      throw new BusinessException(ErrorCode.MEMORY_NOT_FOUND);
    }
    // Single use: a leaked link is worth nothing once the customer has opened it.
    redis.delete(key);
    return body;
  }

  private String sha256(String body) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.INTERNAL);
    }
  }
}
