package com.app.service.security;

import com.app.constant.ErrorCode;
import com.app.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 over the raw body of a CMS webhook. A publish re-embeds a document, so an
 * unauthenticated call here would be a way to poison retrieval (design §12).
 */
@Component
public class WebhookVerifier {

  private static final String ALGORITHM = "HmacSHA256";

  private final byte[] secret;

  public WebhookVerifier(@Value("${app.mi.webhook-secret}") String secret) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  public void verify(String rawBody, String signature) {
    if (signature == null || signature.isBlank()) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret, ALGORITHM));
      String expected =
          HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
      // Constant-time compare so a wrong signature leaks nothing through timing.
      if (!MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.UTF_8),
          signature.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
      }
    } catch (java.security.GeneralSecurityException e) {
      throw new BusinessException(ErrorCode.INTERNAL);
    }
  }
}
