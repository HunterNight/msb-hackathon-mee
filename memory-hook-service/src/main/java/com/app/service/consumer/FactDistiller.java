package com.app.service.consumer;

import com.app.constant.AppConstants;
import com.app.constant.MemoryConstants;
import com.app.model.EventFact;
import com.app.repository.EventFactRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reduces one event to the minimum the memoriser needs: what kind of movement, against which
 * opaque key, how much and on which day. Merchant strings, notes and account numbers are dropped
 * here and never reach storage (design §4 "Minimisation").
 */
@Component
public class FactDistiller {

  private final EventFactRepository facts;

  public FactDistiller(EventFactRepository facts) {
    this.facts = facts;
  }

  @Transactional
  public void distil(String pseudoId, String eventId, String eventType, Instant eventTime,
      Map<String, Object> data) {

    String factType = MemoryConstants.EVENT_FACTS.get(eventType);
    if (factType == null) {
      return;
    }
    String refKey = refKey(factType, data);
    if (refKey == null) {
      return;
    }
    if (facts.existsByEventIdAndFactTypeAndRefKey(eventId, factType, refKey)) {
      return;
    }

    EventFact fact = new EventFact();
    fact.setPseudoId(pseudoId);
    fact.setFactType(factType);
    fact.setRefKey(refKey);
    fact.setAmount(money(data, "amount", "principal", "saved", "closing"));
    fact.setCategoryCode(category(data));
    fact.setOccurredAt(eventTime);
    fact.setDayOfMonth(LocalDate.ofInstant(eventTime, AppConstants.USER_ZONE).getDayOfMonth());
    fact.setPct(integer(data, "pct"));
    fact.setEventId(eventId);
    facts.save(fact);

    // A ledger event that carries the resulting balance also tells us how much is sitting idle.
    BigDecimal balance = money(data, "balanceAfter");
    if (balance != null) {
      EventFact balanceFact = new EventFact();
      balanceFact.setPseudoId(pseudoId);
      balanceFact.setFactType(MemoryConstants.FACT_BALANCE);
      balanceFact.setRefKey("primary");
      balanceFact.setAmount(balance);
      balanceFact.setOccurredAt(eventTime);
      balanceFact.setEventId(eventId);
      if (!facts.existsByEventIdAndFactTypeAndRefKey(
          eventId, MemoryConstants.FACT_BALANCE, "primary")) {
        facts.save(balanceFact);
      }
    }
  }

  /** An id or a code — never a name, and never anything that identifies an account. */
  private String refKey(String factType, Map<String, Object> data) {
    List<String> candidates =
        switch (factType) {
          case MemoryConstants.FACT_TRANSFER -> List.of("beneficiaryId", "transferId", "ref");
          case MemoryConstants.FACT_BILL -> List.of("billerCode", "billId");
          case MemoryConstants.FACT_DEPOSIT -> List.of("depositRef");
          case MemoryConstants.FACT_LOAN -> List.of("loanRef");
          case MemoryConstants.FACT_GOAL -> List.of("goalId", "conversationId", "customerId");
          case MemoryConstants.FACT_CARD_DUE -> List.of("statementId", "cardId");
          case MemoryConstants.FACT_NUDGE_DISMISSED -> List.of("triggerCode");
          case MemoryConstants.FACT_INCOME -> List.of("accountId", "ref");
          default -> List.of("categoryCode", "accountId", "ref");
        };
    return candidates.stream()
        .map(data::get)
        .filter(java.util.Objects::nonNull)
        .map(Object::toString)
        .findFirst()
        .orElse(null);
  }

  private String category(Map<String, Object> data) {
    Object category = data.get("categoryCode");
    if (category == null) {
      return null;
    }
    String value = category.toString();
    return MemoryConstants.CATEGORIES.contains(value) ? value : "OTHER";
  }

  private BigDecimal money(Map<String, Object> data, String... keys) {
    return Optional.ofNullable(first(data, keys))
        .map(value -> new BigDecimal(value.toString()).setScale(0, RoundingMode.HALF_UP))
        .orElse(null);
  }

  private Integer integer(Map<String, Object> data, String key) {
    Object value = data.get(key);
    return value == null ? null : new BigDecimal(value.toString()).intValue();
  }

  private Object first(Map<String, Object> data, String... keys) {
    for (String key : keys) {
      Object value = data.get(key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }
}
