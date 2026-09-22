package com.app.service.job;

import com.app.constant.MemoryConstants;
import com.app.model.ErasureRequest;
import com.app.repository.AccessAuditRepository;
import com.app.repository.ErasureRequestRepository;
import com.app.repository.EventFactRepository;
import com.app.repository.IngestLogRepository;
import com.app.service.ErasureService;
import com.app.service.MemoryRecordService;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retention, erasure and the poisoning scan. Everything here is idempotent and holds a ShedLock,
 * so a second replica running the same minute changes nothing (design §8).
 */
@Component
public class MemoryJobs {

  private static final Logger log = LoggerFactory.getLogger(MemoryJobs.class);
  private static final int BATCH = 500;

  private final MemoryRecordService recordService;
  private final ErasureService erasureService;
  private final ErasureRequestRepository erasures;
  private final AccessAuditRepository audits;
  private final IngestLogRepository ingestLog;
  private final EventFactRepository facts;

  public MemoryJobs(
      MemoryRecordService recordService,
      ErasureService erasureService,
      ErasureRequestRepository erasures,
      AccessAuditRepository audits,
      IngestLogRepository ingestLog,
      EventFactRepository facts) {
    this.recordService = recordService;
    this.erasureService = erasureService;
    this.erasures = erasures;
    this.audits = audits;
    this.ingestLog = ingestLog;
    this.facts = facts;
  }

  /** Records past their TTL, plus the rolling windows for facts, audit and the ingest log. */
  @Scheduled(cron = "0 15 2 * * *")
  @SchedulerLock(name = "memory-retention", lockAtLeastFor = "PT1M", lockAtMostFor = "PT20M")
  public void retention() {
    int expired = recordService.expire(Instant.now(), BATCH);
    int oldFacts = facts.deleteOlderThan(Instant.now().minus(MemoryConstants.SNAPSHOT_WINDOW));
    int oldAudit = audits.deleteOlderThan(Instant.now().minus(MemoryConstants.AUDIT_RETENTION));
    int oldIngest =
        ingestLog.deleteOlderThan(Instant.now().minus(MemoryConstants.INGEST_LOG_RETENTION));
    log.info(
        "retention expired={} facts={} audit={} ingest={}", expired, oldFacts, oldAudit, oldIngest);
  }

  /** Erasure runs asynchronously but well inside the 24-hour SLA the customer was promised. */
  @Scheduled(fixedDelayString = "PT2M")
  @SchedulerLock(name = "memory-erasure", lockAtLeastFor = "PT30S", lockAtMostFor = "PT15M")
  public void erasure() {
    List<ErasureRequest> queued =
        erasures.findByStatusIn(
            List.of(MemoryConstants.ERASURE_QUEUED, MemoryConstants.ERASURE_RUNNING));
    for (ErasureRequest request : queued) {
      erasureService.execute(request.getId());
    }
  }
}
