package com.app.service;

import com.app.dto.request.MiRequests.ConsentsRequest;
import com.app.dto.request.MiRequests.UpdateSettingsRequest;
import com.app.dto.response.MiResponses.AgentSummaryDto;
import com.app.dto.response.MiResponses.ConsentsResponse;
import com.app.dto.response.MiResponses.ExplainResponse;
import com.app.dto.response.MiResponses.NudgeDto;
import com.app.dto.response.MiResponses.SettingsResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Screen 09 plus the smaller Level 3 surfaces: agents, nudges, consents, explanations. */
public interface MiSettingsService {

  SettingsResponse settings(UUID customerId);

  SettingsResponse update(UUID customerId, UpdateSettingsRequest request);

  SettingsResponse pause(UUID customerId, boolean paused);

  ConsentsResponse consents(UUID customerId);

  ConsentsResponse updateConsents(UUID customerId, ConsentsRequest request);

  List<AgentSummaryDto> agents(Locale locale);

  List<NudgeDto> nudges(UUID customerId, Locale locale);

  ExplainResponse explain(UUID customerId, UUID decisionId, Locale locale);

  /** The internal autonomy question the bill and card jobs ask before acting. */
  com.app.dto.response.MiResponses.DecideResponse decide(
      UUID customerId, String type, java.math.BigDecimal amount, Boolean beneficiarySaved);
}
