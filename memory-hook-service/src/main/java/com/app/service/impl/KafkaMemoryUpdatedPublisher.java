package com.app.service.impl;

import com.app.constant.MemoryConstants;
import com.app.service.publisher.MemoryUpdatedPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class KafkaMemoryUpdatedPublisher implements MemoryUpdatedPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaMemoryUpdatedPublisher.class);

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper objectMapper;

  public KafkaMemoryUpdatedPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
    this.kafka = kafka;
    this.objectMapper = objectMapper;
  }

  @Override
  public void published(String pseudoId, List<String> kinds, int version) {
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "pseudoId", pseudoId,
                "kinds", kinds,
                "version", version,
                "at", Instant.now().toString()));
    kafka.send(MemoryConstants.TOPIC_MEMORY_UPDATED, pseudoId, body);
  }

  @Override
  public long tombstone(String pseudoId) {
    try {
      // A null value on a compacted topic is what actually removes the key for every consumer.
      return kafka.send(MemoryConstants.TOPIC_MEMORY_UPDATED, pseudoId, null)
          .get()
          .getRecordMetadata()
          .offset();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return -1L;
    } catch (ExecutionException e) {
      log.warn("tombstone publish failed for {}", pseudoId.substring(0, 12));
      return -1L;
    }
  }
}
