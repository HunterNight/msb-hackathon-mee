package com.app.service.privacy;

import com.app.constant.MemoryConstants;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Strips the identifiers that must never reach a prompt, a log line or a stored field. Applied on
 * the way in (event payloads, chat text) and again on the way out (design §4).
 */
@Component
public class PiiRedactor {

  /** Any run of 8+ digits: PANs, account numbers, national ids and phone numbers all qualify. */
  private static final Pattern LONG_DIGITS = Pattern.compile("\\d[\\d .-]{6,}\\d");

  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern VN_PHONE = Pattern.compile("(?:\\+?84|0)\\d{9,10}");
  private static final Pattern URL = Pattern.compile("https?://\\S+");
  private static final Pattern DIGIT_RUN = Pattern.compile("\\d{8,}");

  public String redact(String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    String out = EMAIL.matcher(text).replaceAll(MemoryConstants.REDACTION);
    out = URL.matcher(out).replaceAll(MemoryConstants.REDACTION);
    out = VN_PHONE.matcher(out).replaceAll(MemoryConstants.REDACTION);
    out = LONG_DIGITS.matcher(out).replaceAll(MemoryConstants.REDACTION);
    return out.trim();
  }

  /** True when the text still carries an identifier after redaction would have been applied. */
  public boolean containsIdentifier(String text) {
    if (text == null) {
      return false;
    }
    String compact = text.replaceAll("[ .-]", "");
    return DIGIT_RUN.matcher(compact).find()
        || EMAIL.matcher(text).find()
        || VN_PHONE.matcher(compact).find();
  }
}
