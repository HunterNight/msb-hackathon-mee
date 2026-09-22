package com.app.service.consumer;

import com.app.constant.MemoryConstants;
import com.app.constant.Topics;
import com.app.service.IngestService;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * The Kafka side of ingest and nothing more: unwrap the CloudEvents binary envelope and hand it to
 * {@link IngestService}, which owns the transaction (design §1).
 */
@Component
public class EventConsumer {

  private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);
  private static final String CE_ID = "ce_id";
  private static final String CE_TYPE = "ce_type";
  private static final String CE_SOURCE = "ce_source";
  private static final String CE_SUBJECT = "ce_subject";

  private final IngestService ingestService;

  public EventConsumer(IngestService ingestService) {
    this.ingestService = ingestService;
  }

  @KafkaListener(
      topics = {
        Topics.ACCOUNT,
        Topics.TRANSFER,
        Topics.SAVING,
        Topics.LENDING,
        Topics.CARD,
        Topics.BILL,
        Topics.ONBOARDING,
        Topics.MI
      },
      groupId = MemoryConstants.CONSUMER_GROUP)
  public void onEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
    try {
      ingestService.consume(
          header(record, CE_ID),
          header(record, CE_TYPE),
          header(record, CE_SOURCE),
          header(record, CE_SUBJECT),
          record.value());
      ack.acknowledge();
    } catch (RuntimeException e) {
      // Not acknowledged on purpose: the container retries and then routes to the topic DLQ.
      log.warn("ingest failed for event {}", header(record, CE_ID));
      throw e;
    }
  }

  private String header(ConsumerRecord<String, String> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }
}
