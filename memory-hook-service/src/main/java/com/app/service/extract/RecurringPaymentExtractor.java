package com.app.service.extract;

import com.app.constant.MemoryConstants;
import com.app.model.EventFact;
import com.app.repository.EventFactRepository;
import com.app.service.MemoryRecordService.Candidate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Three or more monthly transfers to the same beneficiary within ±10% of each other become a
 * HABIT. The record names the day, not the amount and not the counterparty (design §5).
 */
@Component
public class RecurringPaymentExtractor implements MemoryExtractor {

  private final EventFactRepository facts;

  public RecurringPaymentExtractor(EventFactRepository facts) {
    this.facts = facts;
  }

  @Override
  public boolean supports(String eventType) {
    return MemoryConstants.EVENT_TRANSFER_COMPLETED.equals(eventType)
        || MemoryConstants.EVENT_BILL_PAID.equals(eventType);
  }

  @Override
  public List<Candidate> extract(ExtractionContext context) {
    String refKey = refKey(context);
    if (refKey == null) {
      return List.of();
    }
    String factType =
        MemoryConstants.EVENT_BILL_PAID.equals(context.eventType())
            ? MemoryConstants.FACT_BILL
            : MemoryConstants.FACT_TRANSFER;

    List<EventFact> history =
        facts.findByPseudoIdAndFactTypeAndRefKeyOrderByOccurredAtDesc(
            context.pseudoId(), factType, refKey);
    if (history.size() < MemoryConstants.RECURRING_MIN_OCCURRENCES) {
      return List.of();
    }

    BigDecimal median = median(history);
    long consistent =
        history.stream()
            .filter(fact -> fact.getAmount() != null && withinTolerance(fact.getAmount(), median))
            .count();
    if (consistent < MemoryConstants.RECURRING_MIN_OCCURRENCES) {
      return List.of();
    }

    Integer day = commonDay(history);
    if (day == null) {
      return List.of();
    }

    String text =
        MemoryConstants.FACT_BILL.equals(factType)
            ? "Thường thanh toán hóa đơn vào ngày " + day + " hàng tháng"
            : "Thường chuyển tiền định kỳ vào ngày " + day + " hàng tháng";

    return List.of(
        new Candidate(
            context.pseudoId(),
            context.dek(),
            MemoryConstants.KIND_HABIT,
            text,
            MemoryConstants.KIND_HABIT + ":" + factType + ":" + refKey,
            confidence(consistent),
            MemoryConstants.SOURCE_EVENTS,
            Map.of(referenceField(factType), refKey),
            context.eventId(),
            context.eventType(),
            context.eventTime()));
  }

  private String refKey(ExtractionContext context) {
    String beneficiary = context.string("beneficiaryId");
    if (beneficiary != null) {
      return beneficiary;
    }
    String biller = context.string("billerCode");
    return biller != null ? biller : context.string("ref");
  }

  private String referenceField(String factType) {
    return MemoryConstants.FACT_BILL.equals(factType) ? "billerCode" : "beneficiaryId";
  }

  private boolean withinTolerance(BigDecimal amount, BigDecimal median) {
    if (median.signum() == 0) {
      return false;
    }
    BigDecimal delta = amount.subtract(median).abs();
    return delta.divide(median, 4, RoundingMode.HALF_UP)
        .compareTo(MemoryConstants.RECURRING_AMOUNT_TOLERANCE)
        <= 0;
  }

  private BigDecimal median(List<EventFact> history) {
    List<BigDecimal> amounts =
        history.stream()
            .map(EventFact::getAmount)
            .filter(java.util.Objects::nonNull)
            .sorted()
            .toList();
    return amounts.isEmpty() ? BigDecimal.ZERO : amounts.get(amounts.size() / 2);
  }

  /** The dominant day, tolerating the usual weekend drift. */
  private Integer commonDay(List<EventFact> history) {
    Map<Integer, Long> byDay =
        history.stream()
            .map(EventFact::getDayOfMonth)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.groupingBy(day -> day,
                java.util.stream.Collectors.counting()));
    Integer dominant =
        byDay.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    if (dominant == null) {
      return null;
    }
    long near =
        history.stream()
            .map(EventFact::getDayOfMonth)
            .filter(java.util.Objects::nonNull)
            .filter(day -> Math.abs(day - dominant) <= MemoryConstants.RECURRING_DAY_TOLERANCE)
            .count();
    return near >= MemoryConstants.RECURRING_MIN_OCCURRENCES ? dominant : null;
  }

  private BigDecimal confidence(long occurrences) {
    return BigDecimal.valueOf(Math.min(0.95, 0.70 + 0.05 * occurrences))
        .setScale(3, RoundingMode.HALF_UP)
        .max(MemoryConstants.CONFIDENCE_MIN)
        .min(BigDecimal.ONE);
  }
}
