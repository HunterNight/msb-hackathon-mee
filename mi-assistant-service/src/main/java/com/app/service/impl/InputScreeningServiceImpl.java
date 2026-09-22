package com.app.service.impl;

import com.app.constant.ErrorCode;
import com.app.constant.MiConstants;
import com.app.dto.internal.MiInternal.ScreeningResult;
import com.app.exception.BusinessException;
import com.app.model.SafetyFlag;
import com.app.repository.SafetyFlagRepository;
import com.app.service.security.InputScreeningService;
import com.app.service.security.PiiRedactor;
import com.app.service.security.SafetyClassifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InputScreeningServiceImpl implements InputScreeningService {

  private static final String MINUTE_KEY = "mi:turns:m:";
  private static final String DAY_KEY = "mi:turns:d:";
  /** Zero-width and other invisible characters used to smuggle instructions past a reader. */
  private static final String CONTROL_CHARS = "[\\p{Cc}\\p{Cf}&&[^\\n\\r\\t]]";

  private final SafetyClassifier classifier;
  private final PiiRedactor redactor;
  private final SafetyFlagRepository flags;
  private final StringRedisTemplate redis;

  public InputScreeningServiceImpl(
      SafetyClassifier classifier,
      PiiRedactor redactor,
      SafetyFlagRepository flags,
      StringRedisTemplate redis) {
    this.classifier = classifier;
    this.redactor = redactor;
    this.flags = flags;
    this.redis = redis;
  }

  @Override
  public ScreeningResult screen(UUID customerId, String text) {
    if (text != null && text.length() > MiConstants.INPUT_MAX_CHARS) {
      throw new BusinessException(
          ErrorCode.MI_RATE_LIMITED, Map.of("limit", MiConstants.INPUT_MAX_CHARS));
    }
    rateLimit(customerId);

    String stripped = text == null ? "" : text.replaceAll(CONTROL_CHARS, "").trim();
    // Secrets are redacted before anything else sees them, including the classifier.
    String sanitised = redactor.redact(stripped);

    SafetyClassifier.Verdict verdict = classifier.classify(stripped);
    if (MiConstants.LABEL_ILLEGAL.equals(verdict.label())
        || MiConstants.LABEL_SELF_HARM.equals(verdict.label())) {
      return new ScreeningResult(verdict.label(), false, sanitised, verdict.reasonKey());
    }
    if (classifier.asksForSecrets(stripped)) {
      return new ScreeningResult(
          MiConstants.LABEL_SAFE, false, sanitised, "mi.reply.doNotShareSecrets");
    }
    boolean injection = MiConstants.LABEL_INJECTION.equals(verdict.label());
    // An injection attempt is still answered — with tools disabled and the reply marked GUARDED.
    return new ScreeningResult(verdict.label(), injection, sanitised, null);
  }

  @Override
  public boolean chunkIsSafe(String content) {
    return MiConstants.LABEL_SAFE.equals(classifier.classify(content).label());
  }

  @Override
  @Transactional
  public boolean flag(UUID customerId, String label, String excerpt) {
    SafetyFlag flag = new SafetyFlag();
    flag.setCustomerId(customerId);
    flag.setLabel(label);
    flag.setExcerpt(excerpt == null ? null : redactor.redact(excerpt.substring(0,
        Math.min(200, excerpt.length()))));
    flags.save(flag);
    long recent =
        flags.countByCustomerIdAndLabelAndAtAfter(
            customerId, label, Instant.now().minus(Duration.ofHours(24)));
    return recent >= MiConstants.ILLEGAL_FLAG_THRESHOLD_24H;
  }

  private void rateLimit(UUID customerId) {
    check(MINUTE_KEY + customerId, Duration.ofMinutes(1), MiConstants.TURNS_PER_MINUTE, 60);
    check(DAY_KEY + customerId, Duration.ofDays(1), MiConstants.TURNS_PER_DAY, 86400);
  }

  private void check(String key, Duration window, int limit, int retryAfterSeconds) {
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redis.expire(key, window);
    }
    if (count != null && count > limit) {
      throw new BusinessException(
          ErrorCode.MI_RATE_LIMITED,
          Map.of("limit", limit, "retryAfterSeconds", retryAfterSeconds));
    }
  }
}
