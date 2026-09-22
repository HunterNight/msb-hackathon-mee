package com.app.service.privacy;

import com.app.constant.ErrorCode;
import com.app.constant.MemoryConstants;
import com.app.exception.BusinessException;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The gate every candidate record passes before it can be stored. Rejections carry the reason the
 * API contract promises in {@code details.reason} (MH-003).
 */
@Component
public class DlpValidator {

  private final PiiRedactor redactor;
  private final SensitiveClassifier classifier;

  public DlpValidator(PiiRedactor redactor, SensitiveClassifier classifier) {
    this.redactor = redactor;
    this.classifier = classifier;
  }

  /**
   * @return the sanitised abstract text; the caller stores this and embeds it
   */
  public String validateAndAbstract(String kind, String text) {
    if (!MemoryConstants.KINDS.contains(kind)) {
      throw reject(MemoryConstants.REJECT_SCHEMA);
    }
    if (text == null || text.isBlank()) {
      throw reject(MemoryConstants.REJECT_SCHEMA);
    }
    if (text.length() > MemoryConstants.RECORD_TEXT_MAX) {
      throw reject(MemoryConstants.REJECT_LENGTH);
    }
    if (classifier.isSensitive(text)) {
      throw reject(MemoryConstants.REJECT_SENSITIVE);
    }
    String abstracted = redactor.redact(text);
    // A redaction that had to fire means the caller sent an identifier: refuse rather than store
    // the mangled remains, so the customer sees a clear rejection instead of "[…]" in their memory.
    if (redactor.containsIdentifier(text) || abstracted.contains(MemoryConstants.REDACTION)) {
      throw reject(MemoryConstants.REJECT_PII);
    }
    return abstracted;
  }

  /** Same rules, but for machine-generated candidates where dropping beats failing the batch. */
  public boolean acceptable(String kind, String text) {
    try {
      validateAndAbstract(kind, text);
      return true;
    } catch (BusinessException e) {
      return false;
    }
  }

  private BusinessException reject(String reason) {
    return new BusinessException(ErrorCode.RECORD_REJECTED, Map.of("reason", reason));
  }
}
