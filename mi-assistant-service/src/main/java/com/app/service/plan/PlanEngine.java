package com.app.service.plan;

import com.app.dto.response.MiResponses.PlanDto;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Durable, human-supervised workflows. A step that moves money is approved exactly the way a
 * proposal is, step-up included (design §M3.10).
 */
public interface PlanEngine {

  List<PlanDto> list(UUID customerId, String status);

  PlanDto get(UUID customerId, UUID planId);

  /** @param stepSeq null approves every pending step that is within the customer's limits */
  PlanDto approve(UUID customerId, UUID planId, Integer stepSeq, String stepUpToken, Locale locale);

  PlanDto pause(UUID customerId, UUID planId);

  PlanDto resume(UUID customerId, UUID planId);

  PlanDto cancel(UUID customerId, UUID planId);
}
