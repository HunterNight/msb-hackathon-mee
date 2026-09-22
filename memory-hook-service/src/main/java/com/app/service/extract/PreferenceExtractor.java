package com.app.service.extract;

import com.app.constant.MemoryConstants;
import com.app.model.EventFact;
import com.app.repository.EventFactRepository;
import com.app.service.MemoryRecordService.Candidate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Two sources of preference: what the customer chose (a deposit term) and what they kept refusing
 * (three dismissals of the same nudge is an answer).
 */
@Component
public class PreferenceExtractor implements MemoryExtractor {

  private final EventFactRepository facts;

  public PreferenceExtractor(EventFactRepository facts) {
    this.facts = facts;
  }

  @Override
  public boolean supports(String eventType) {
    return MemoryConstants.EVENT_NUDGE_DISMISSED.equals(eventType)
        || "msb.saving.depositOpened.v1".equals(eventType);
  }

  @Override
  public List<Candidate> extract(ExtractionContext context) {
    if (MemoryConstants.EVENT_NUDGE_DISMISSED.equals(context.eventType())) {
      return dismissal(context);
    }
    Integer term = context.integer("termMonths");
    if (term == null) {
      return List.of();
    }
    return List.of(
        new Candidate(
            context.pseudoId(),
            context.dek(),
            MemoryConstants.KIND_PREFERENCE,
            "Thường chọn kỳ hạn gửi " + term + " tháng",
            MemoryConstants.KIND_PREFERENCE + ":deposit-term",
            new BigDecimal("0.750"),
            MemoryConstants.SOURCE_EVENTS,
            Map.of(),
            context.eventId(),
            context.eventType(),
            context.eventTime()));
  }

  private List<Candidate> dismissal(ExtractionContext context) {
    String triggerCode = context.string("triggerCode");
    if (triggerCode == null) {
      return List.of();
    }
    List<EventFact> history =
        facts.findByPseudoIdAndFactTypeAndRefKeyOrderByOccurredAtDesc(
            context.pseudoId(), MemoryConstants.FACT_NUDGE_DISMISSED, triggerCode);
    if (history.size() < MemoryConstants.DISMISSALS_FOR_PREFERENCE) {
      return List.of();
    }
    return List.of(
        new Candidate(
            context.pseudoId(),
            context.dek(),
            MemoryConstants.KIND_PREFERENCE,
            "Không muốn nhận gợi ý loại này",
            MemoryConstants.KIND_PREFERENCE + ":nudge:" + triggerCode,
            new BigDecimal("0.900"),
            MemoryConstants.SOURCE_EVENTS,
            Map.of("triggerCode", triggerCode),
            context.eventId(),
            context.eventType(),
            context.eventTime()));
  }
}
