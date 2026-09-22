package com.app.service.crypto;

/**
 * The bridge between a request's {@code customerId} and the customer's key material. Nothing else
 * in the service is allowed to hold the real id (design §3).
 */
public interface KeyService {

  /** HMAC namespace used by records, vectors, audit and Kafka keys. */
  String pseudoId(java.util.UUID customerId);

  /** Resolves (creating on first use) the customer's key row and returns the unwrapped DEK. */
  byte[] dek(java.util.UUID customerId);

  /** Same, without creating: empty when the customer has no memory yet. */
  java.util.Optional<byte[]> dekIfPresent(String pseudoId);

  /** Crypto-shredding: after this the encrypted fields are unrecoverable. */
  void destroy(String pseudoId);

  /** Re-wraps every DEK under a new KEK version; the DEKs themselves are unchanged. */
  KeyRotationResult rotate(String kekVersion);

  record KeyRotationResult(int rewrapped, int failed) {}
}
