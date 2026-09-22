package com.app.service.tool;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Vietnamese amount phrases: "2 triệu", "500k", "1tr5", "2m", "10.000.000 ₫" (design §3.2).
 * Returns {@code null} rather than guessing — a wrong amount must never reach a proposal.
 */
@Component
public class AmountParser {

  private static final BigDecimal THOUSAND = new BigDecimal("1000");
  private static final BigDecimal MILLION = new BigDecimal("1000000");
  private static final BigDecimal BILLION = new BigDecimal("1000000000");

  /** "1tr5" / "2 triệu 300" — a unit followed by a remainder in the next-smaller unit. */
  private static final Pattern COMPOUND =
      Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(tr|trieu|ty|ti|m|k|nghin|ngan)\\s*(\\d{1,3})?");

  private static final Pattern PLAIN = Pattern.compile("(\\d{1,3}(?:[.\\s]\\d{3})+|\\d{4,})");

  public BigDecimal parse(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String folded = fold(text);

    Matcher compound = COMPOUND.matcher(folded);
    if (compound.find()) {
      BigDecimal base = new BigDecimal(compound.group(1).replace(',', '.'));
      BigDecimal unit = unitOf(compound.group(2));
      BigDecimal amount = base.multiply(unit);
      String remainder = compound.group(3);
      if (remainder != null) {
        // "1tr5" means 1.500.000: the tail is a fraction of the unit, written without zeros.
        BigDecimal tail =
            new BigDecimal(remainder)
                .multiply(unit)
                .divide(BigDecimal.TEN.pow(remainder.length()), 0, java.math.RoundingMode.HALF_UP);
        amount = amount.add(tail);
      }
      return amount.setScale(0, java.math.RoundingMode.HALF_UP);
    }

    Matcher plain = PLAIN.matcher(folded);
    if (plain.find()) {
      String digits = plain.group(1).replaceAll("[.\\s]", "");
      return new BigDecimal(digits);
    }
    return null;
  }

  private BigDecimal unitOf(String unit) {
    return switch (unit) {
      case "k", "nghin", "ngan" -> THOUSAND;
      case "ty", "ti" -> BILLION;
      default -> MILLION;
    };
  }

  private String fold(String text) {
    return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replace('đ', 'd')
        .replaceAll("\\s+", " ");
  }
}
