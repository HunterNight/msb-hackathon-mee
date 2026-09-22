package com.app.service.impl;

import com.app.constant.AppConstants;
import com.app.model.BaseEntity;
import com.app.service.EventBus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Emits CloudEvents in binary content mode: the envelope travels as {@code ce-*} headers and the
 * body is the event data (guideline 06 §3).
 */
@Service
@ConditionalOnProperty(name = "app.events.adapter", havingValue = "kafka", matchIfMissing = true)
public class KafkaEventBus implements EventBus {

  private final KafkaTemplate<String, String> kafka;

  public KafkaEventBus(KafkaTemplate<String, String> kafka) {
    this.kafka = kafka;
  }

  @Override
  public void publish(String eventType, UUID subject, String payloadJson) {
    ProducerRecord<String, String> record =
        new ProducerRecord<>(AppConstants.topicFor(eventType), subject.toString(), payloadJson);

    header(record, "ce_specversion", "1.0");
    header(record, "ce_id", BaseEntity.newId().toString());
    header(record, "ce_source", "/msb/" + AppConstants.SERVICE_NAME);
    header(record, "ce_type", eventType);
    header(record, "ce_subject", subject.toString());
    header(record, "ce_time", Instant.now().toString());
    header(record, "ce_datacontenttype", "application/json");
    String requestId = MDC.get(AppConstants.MDC_REQUEST_ID);
    if (requestId != null) {
      header(record, "ce_requestid", requestId);
    }
    kafka.send(record);
  }

  private void header(ProducerRecord<String, String> record, String key, String value) {
    record.headers().add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
  }
}
