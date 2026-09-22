package com.app.service.security;

import com.app.constant.MiConstants;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Labels a turn {@code SAFE | INJECTION | ILLEGAL | SELF_HARM | OFF_TOPIC}. The rules run first
 * and are what the red-team set is asserted against; a model classifier can only add labels, never
 * clear one the rules raised (design §12.2).
 */
@Component
public class SafetyClassifier {

  public record Verdict(String label, String reasonKey) {}

  public Verdict classify(String text) {
    String folded = fold(text);

    for (String marker : MiConstants.SELF_HARM_MARKERS) {
      if (folded.contains(marker)) {
        return new Verdict(MiConstants.LABEL_SELF_HARM, "mi.reply.refuse.selfHarm");
      }
    }
    for (Map.Entry<String, String> entry : MiConstants.ILLEGAL_MARKERS.entrySet()) {
      if (folded.contains(entry.getKey())) {
        return new Verdict(MiConstants.LABEL_ILLEGAL, "mi.reply.refuse." + entry.getValue());
      }
    }
    for (String marker : MiConstants.INJECTION_MARKERS) {
      if (folded.contains(marker)) {
        return new Verdict(MiConstants.LABEL_INJECTION, null);
      }
    }
    return new Verdict(MiConstants.LABEL_SAFE, null);
  }

  /**
   * True when the customer looks to be <em>disclosing</em> a secret, not merely naming one.
   * "MSB có bao giờ hỏi mã OTP không?" is a safety question and deserves a real answer; only a
   * secret followed by something that looks like its value earns the warning.
   */
  public boolean asksForSecrets(String text) {
    String folded = fold(text);
    for (String marker : MiConstants.SECRET_MARKERS) {
      int at = folded.indexOf(marker);
      if (at < 0) {
        continue;
      }
      String after = folded.substring(Math.min(folded.length(), at + marker.length()));
      if (DISCLOSURE.matcher(after).find()) {
        return true;
      }
    }
    return false;
  }

  /**
   * "là 123456", ": 8421", and "của tôi là 123456" — a value offered up after naming the secret.
   * A few possessive words may sit in between; anything longer is a sentence about the secret,
   * not a disclosure of it.
   */
  private static final java.util.regex.Pattern DISCLOSURE =
      java.util.regex.Pattern.compile(
          "^(?:\\s+(?:cua|toi|minh|ban|nay|hien|tai|the|so|moi|vua|duoc|gui|nhan)){0,4}"
              + "\\s*(?:la|:|=)?\\s*\\d{4,}");

  /** Lower-cased and diacritic-free, so "bỏ qua hướng dẫn" and "bo qua huong dan" both match. */
  private String fold(String text) {
    if (text == null) {
      return "";
    }
    String lower = text.toLowerCase(Locale.ROOT);
    return Normalizer.normalize(lower, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replace('đ', 'd')
        .replaceAll("\\s+", " ");
  }
}
