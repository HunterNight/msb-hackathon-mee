package com.app.service;

/**
 * The ingest pipeline, kept separate from the Kafka plumbing so the admin replay endpoint can feed
 * exactly the same path as a live delivery (design §9).
 */
public interface IngestService {

  /**
   * @param eventId CloudEvents {@code ce_id} — the idempotency key of the whole pipeline
   * @param source CloudEvents {@code ce_source}, checked against the producer allow-list
   * @param subject CloudEvents {@code ce_subject}, the fallback customer id
   */
  void consume(String eventId, String eventType, String source, String subject, String body);
}
