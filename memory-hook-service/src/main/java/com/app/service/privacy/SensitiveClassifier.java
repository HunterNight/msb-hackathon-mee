package com.app.service.privacy;

import com.app.constant.MemoryConstants;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Special-category data (health, religion, politics, sexual orientation, biometrics) is never
 * stored: the extractor drops it and the ingest log records {@code DROPPED_SENSITIVE} (design §3).
 */
@Component
public class SensitiveClassifier {

  public boolean isSensitive(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String lower = text.toLowerCase(Locale.ROOT);
    return MemoryConstants.SENSITIVE_PATTERNS.stream().anyMatch(lower::contains);
  }

  public String classify(String text, String kind) {
    if (isSensitive(text)) {
      return MemoryConstants.CLASS_SENSITIVE;
    }
    return MemoryConstants.KIND_NICKNAME.equals(kind)
        ? MemoryConstants.CLASS_CONFIDENTIAL
        : MemoryConstants.CLASS_INTERNAL;
  }
}
