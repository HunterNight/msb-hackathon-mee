package com.app.service.job;

import com.app.constant.AppConstants;
import com.app.model.OutboxEvent;
import com.app.repository.OutboxEventRepository;
import com.app.service.EventBus;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the transactional outbox. ShedLock keeps exactly one replica publishing
 * (guideline 01 §7b).
 */
@Component
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxEventRepository repository;
  private final EventBus eventBus;

  public OutboxPublisher(OutboxEventRepository repository, EventBus eventBus) {
    this.repository = repository;
    this.eventBus = eventBus;
  }

  @Scheduled(fixedDelayString = "${app.events.publish-interval-ms:1000}")
  @SchedulerLock(name = AppConstants.SERVICE_NAME + "-outbox", lockAtMostFor = "PT1M")
  @Transactional
  public void publishPending() {
    List<OutboxEvent> pending =
        repository.findByPublishedAtIsNullOrderByCreatedAtAsc(
            Limit.of(AppConstants.OUTBOX_BATCH_SIZE));

    for (OutboxEvent event : pending) {
      event.recordAttempt();
      if (event.getAttempts() > AppConstants.OUTBOX_MAX_ATTEMPTS) {
        log.error("outbox event {} exceeded retries, moving on", event.getId());
        event.markPublished();
        continue;
      }
      eventBus.publish(event.getEventType(), event.getAggregateId(), event.getPayload());
      event.markPublished();
    }
  }
}
