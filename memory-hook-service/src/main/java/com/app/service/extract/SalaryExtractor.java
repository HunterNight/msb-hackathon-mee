package com.app.service.extract;

import com.app.constant.MemoryConstants;
import com.app.model.EventFact;
import com.app.repository.EventFactRepository;
import com.app.service.MemoryRecordService.Candidate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Pay day drives most of Mi's proactive behaviour, so it gets its own rule: a credit that is the
 * dominant share of the month's income, landing on roughly the same day twice or more.
 */
@Component
public class SalaryExtractor implements MemoryExtractor {

  private final EventFactRepository facts;

  public SalaryExtractor(EventFactRepository facts) {
    this.facts = facts;
  }

  @Override
  public boolean supports(String eventType) {
    return MemoryConstants.EVENT_SALARY.equals(eventType);
  }

  @Override
  public List<Candidate> extract(ExtractionContext context) {
    List<EventFact> income =
        facts.findByPseudoIdAndFactTypeAndOccurredAtAfter(
            context.pseudoId(),
            MemoryConstants.FACT_INCOME,
            Instant.now().minus(MemoryConstants.SNAPSHOT_WINDOW));
    if (income.size() < MemoryConstants.SALARY_MIN_OCCURRENCES) {
      return List.of();
    }

    Map<Integer, Long> byDay =
        income.stream()
            .map(EventFact::getDayOfMonth)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.groupingBy(day -> day, Collectors.counting()));
    var dominant = byDay.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
    if (dominant == null || dominant.getValue() < MemoryConstants.SALARY_MIN_OCCURRENCES) {
      return List.of();
    }

    BigDecimal total =
        income.stream()
            .map(EventFact::getAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal onDay =
        income.stream()
            .filter(fact -> dominant.getKey().equals(fact.getDayOfMonth()))
            .map(EventFact::getAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.signum() == 0
        || onDay.divide(total, 2, java.math.RoundingMode.HALF_UP)
                .compareTo(MemoryConstants.SALARY_SHARE_MIN)
            < 0) {
      return List.of();
    }

    return List.of(
        new Candidate(
            context.pseudoId(),
            context.dek(),
            MemoryConstants.KIND_HABIT,
            "Nhận lương vào ngày " + dominant.getKey() + " hàng tháng",
            MemoryConstants.KIND_HABIT + ":payday",
            new BigDecimal("0.900"),
            MemoryConstants.SOURCE_EVENTS,
            Map.of(),
            context.eventId(),
            context.eventType(),
            context.eventTime()));
  }
}
