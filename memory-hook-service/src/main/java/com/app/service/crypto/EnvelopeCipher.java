package com.app.service.crypto;

/**
 * Field-level envelope encryption: AES-256-GCM under a per-customer DEK, the DEK itself wrapped by
 * the KEK (design §4 "Encryption at rest").
 */
public interface EnvelopeCipher {

  byte[] encrypt(byte[] dek, String plaintext);

  String decrypt(byte[] dek, byte[] ciphertext);

  /** Wraps a freshly generated DEK; the wrapped form is all that touches the database. */
  byte[] wrap(byte[] kek, byte[] dek);

  byte[] unwrap(byte[] kek, byte[] wrapped);

  byte[] newDek();
}
