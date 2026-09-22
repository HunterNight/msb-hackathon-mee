package com.app.service;

import java.util.UUID;

/**
 * Port over the event backbone. {@code KafkaEventBus} is the default; a Redis Streams adapter is
 * available for local runs only (guideline 01 §1, 06 §3).
 */
public interface EventBus {

  /**
   * @param eventType CloudEvents {@code type}, e.g. {@code msb.transfer.completed.v1}
   * @param subject CloudEvents {@code subject} — the aggregate id
   * @param payloadJson already-serialised event data
   */
  void publish(String eventType, UUID subject, String payloadJson);
}
