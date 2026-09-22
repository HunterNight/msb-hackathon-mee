package com.app.service.crypto;

import com.app.constant.ErrorCode;
import com.app.exception.BusinessException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * The wire format is {@code [12-byte IV][ciphertext||16-byte tag]}, so a stored value carries its
 * own nonce and nothing has to be remembered alongside it.
 */
@Component
public class AesGcmEnvelopeCipher implements EnvelopeCipher {

  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int KEY_BYTES = 32;

  private final SecureRandom random = new SecureRandom();

  @Override
  public byte[] encrypt(byte[] dek, String plaintext) {
    return seal(dek, plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Override
  public String decrypt(byte[] dek, byte[] ciphertext) {
    return new String(open(dek, ciphertext), java.nio.charset.StandardCharsets.UTF_8);
  }

  @Override
  public byte[] wrap(byte[] kek, byte[] dek) {
    return seal(kek, dek);
  }

  @Override
  public byte[] unwrap(byte[] kek, byte[] wrapped) {
    return open(kek, wrapped);
  }

  @Override
  public byte[] newDek() {
    byte[] dek = new byte[KEY_BYTES];
    random.nextBytes(dek);
    return dek;
  }

  private byte[] seal(byte[] key, byte[] plain) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new GCMParameterSpec(TAG_BITS, iv));
      byte[] sealed = cipher.doFinal(plain);
      byte[] out = new byte[iv.length + sealed.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(sealed, 0, out, iv.length, sealed.length);
      return out;
    } catch (Exception e) {
      // Never echo the plaintext or the key material into the error.
      throw new BusinessException(ErrorCode.KMS_UNAVAILABLE);
    }
  }

  private byte[] open(byte[] key, byte[] sealed) {
    try {
      byte[] iv = new byte[IV_BYTES];
      System.arraycopy(sealed, 0, iv, 0, IV_BYTES);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new GCMParameterSpec(TAG_BITS, iv));
      return cipher.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.KMS_UNAVAILABLE);
    }
  }
}
