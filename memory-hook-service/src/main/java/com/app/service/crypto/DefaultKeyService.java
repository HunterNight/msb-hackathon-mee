package com.app.service.crypto;

import com.app.constant.ErrorCode;
import com.app.exception.BusinessException;
import com.app.model.CustomerKey;
import com.app.repository.CustomerKeyRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local KEK implementation of the key custody contract. In GreenNode the {@code kek} bean comes
 * from the KMS; the wrapping format and the pseudonymisation are identical either way, so nothing
 * downstream changes (design §3).
 */
@Service
public class DefaultKeyService implements KeyService {

  private static final String HMAC = "HmacSHA256";

  private final CustomerKeyRepository keys;
  private final EnvelopeCipher cipher;
  private final byte[] pseudonymKey;
  private final byte[] lookupKey;
  private final byte[] kek;
  private final String kekVersion;

  public DefaultKeyService(
      CustomerKeyRepository keys,
      EnvelopeCipher cipher,
      @Value("${app.memory.pseudonym-key}") String pseudonymKey,
      @Value("${app.memory.lookup-key}") String lookupKey,
      @Value("${app.memory.kek}") String kek,
      @Value("${app.memory.kek-version}") String kekVersion) {
    this.keys = keys;
    this.cipher = cipher;
    this.pseudonymKey = pseudonymKey.getBytes(StandardCharsets.UTF_8);
    this.lookupKey = lookupKey.getBytes(StandardCharsets.UTF_8);
    this.kek = sha256(kek);
    this.kekVersion = kekVersion;
  }

  @Override
  public String pseudoId(UUID customerId) {
    return hmac(pseudonymKey, customerId.toString());
  }

  @Override
  @Transactional
  public byte[] dek(UUID customerId) {
    String pseudo = pseudoId(customerId);
    String lookup = hmac(lookupKey, customerId.toString());
    CustomerKey row =
        keys.findById(pseudo)
            .orElseGet(
                () -> {
                  CustomerKey created = new CustomerKey();
                  created.setPseudoId(pseudo);
                  created.setCustomerIdHmac(lookup);
                  created.setDekWrapped(cipher.wrap(kek, cipher.newDek()));
                  created.setKekVersion(kekVersion);
                  return keys.save(created);
                });
    return cipher.unwrap(kek, row.getDekWrapped());
  }

  @Override
  public Optional<byte[]> dekIfPresent(String pseudoId) {
    return keys.findById(pseudoId).map(row -> cipher.unwrap(kek, row.getDekWrapped()));
  }

  @Override
  @Transactional
  public void destroy(String pseudoId) {
    keys.findById(pseudoId).ifPresent(keys::delete);
  }

  @Override
  @Transactional
  public KeyRotationResult rotate(String newKekVersion) {
    int rewrapped = 0;
    int failed = 0;
    for (CustomerKey row : keys.findAll()) {
      try {
        byte[] dek = cipher.unwrap(kek, row.getDekWrapped());
        row.setDekWrapped(cipher.wrap(kek, dek));
        row.setKekVersion(newKekVersion);
        row.setRotatedAt(Instant.now());
        keys.save(row);
        rewrapped++;
      } catch (BusinessException e) {
        // One unreadable row must not abort the rotation of the rest.
        failed++;
      }
    }
    return new KeyRotationResult(rewrapped, failed);
  }

  private String hmac(byte[] key, String value) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(key, HMAC));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.KMS_UNAVAILABLE);
    }
  }

  private static byte[] sha256(String secret) {
    try {
      return java.security.MessageDigest.getInstance("SHA-256")
          .digest(secret.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
