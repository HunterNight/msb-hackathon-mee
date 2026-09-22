package com.app.service.security;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Masks what must never be stored or sent to a model. Applied to the user's turn before it is
 * persisted, and again to every tool result before it enters the prompt (design §12.2, §12.5).
 */
@Component
public class PiiRedactor {

  private static final Pattern PAN = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");
  private static final Pattern ACCOUNT = Pattern.compile("\\b\\d{8,19}\\b");
  private static final Pattern VN_PHONE = Pattern.compile("\\b(?:\\+?84|0)\\d{9,10}\\b");
  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern ID_NUMBER = Pattern.compile("\\b\\d{9}|\\d{12}\\b");
  private static final Pattern OTP = Pattern.compile("(?i)(otp|m[aã] x[aá]c th[uự]c)\\D{0,10}\\d{4,8}");

  private static final String MASK = "****";
  private static final String NUM = "[NUM]";

  /** Storage/prompt form: identifiers become a fixed token, the sentence stays readable. */
  public String redact(String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    String out = OTP.matcher(text).replaceAll(MASK);
    out = EMAIL.matcher(out).replaceAll(MASK);
    out = PAN.matcher(out).replaceAll(MASK);
    out = VN_PHONE.matcher(out).replaceAll(MASK);
    out = ACCOUNT.matcher(out).replaceAll(MASK);
    out = ID_NUMBER.matcher(out).replaceAll(MASK);
    return out;
  }

  /** Transcript form for memory-hook: numbers collapse to a placeholder, names to [PERSON_n]. */
  public String maskForTranscript(String text, java.util.Map<String, String> people) {
    if (text == null) {
      return null;
    }
    String out = redact(text);
    for (var entry : people.entrySet()) {
      out = out.replace(entry.getKey(), entry.getValue());
    }
    return out.replaceAll("\\d[\\d.,]{2,}", NUM);
  }

  /** Last four digits only, the form the design allows in a prompt. */
  public String maskAccount(String accountNumber) {
    if (accountNumber == null || accountNumber.length() < 4) {
      return MASK;
    }
    return "•••• " + accountNumber.substring(accountNumber.length() - 4);
  }
}
