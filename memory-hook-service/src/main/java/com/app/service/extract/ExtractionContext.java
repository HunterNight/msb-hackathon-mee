package com.app.service.extract;

import java.time.Instant;
import java.util.Map;

/**
 * What an extractor gets to see: the pseudonymised namespace, the customer's DEK for writing, and
 * the event's already-minimised data. The real customer id is not in here on purpose (design §4).
 */
public record ExtractionContext(
    String pseudoId,
    byte[] dek,
    String eventId,
    String eventType,
    Instant eventTime,
    Map<String, Object> data) {

  public String string(String key) {
    Object value = data.get(key);
    return value == null ? null : value.toString();
  }

  public java.math.BigDecimal money(String key) {
    Object value = data.get(key);
    return value == null ? null : new java.math.BigDecimal(value.toString()).setScale(0,
        java.math.RoundingMode.HALF_UP);
  }

  public Integer integer(String key) {
    Object value = data.get(key);
    return value == null ? null : Integer.valueOf(new java.math.BigDecimal(value.toString()).intValue());
  }
}
