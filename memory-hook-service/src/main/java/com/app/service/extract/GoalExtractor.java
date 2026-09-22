package com.app.service.extract;

import com.app.constant.MemoryConstants;
import com.app.service.MemoryRecordService.Candidate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** A goal the customer actually created is a stated goal; nothing here is inferred. */
@Component
public class GoalExtractor implements MemoryExtractor {

  @Override
  public boolean supports(String eventType) {
    return MemoryConstants.EVENT_GOAL_PROGRESSED.equals(eventType);
  }

  @Override
  public List<Candidate> extract(ExtractionContext context) {
    String goalId = context.string("goalId");
    Integer pct = context.integer("pct");
    if (goalId == null || pct == null) {
      return List.of();
    }
    // Only worth remembering once there is real commitment behind it.
    if (pct < 10) {
      return List.of();
    }
    return List.of(
        new Candidate(
            context.pseudoId(),
            context.dek(),
            MemoryConstants.KIND_GOAL,
            "Đang tiết kiệm cho một mục tiêu",
            MemoryConstants.KIND_GOAL + ":" + goalId,
            new BigDecimal("0.800"),
            MemoryConstants.SOURCE_EVENTS,
            Map.of("goalId", goalId),
            context.eventId(),
            context.eventType(),
            context.eventTime()));
  }
}
