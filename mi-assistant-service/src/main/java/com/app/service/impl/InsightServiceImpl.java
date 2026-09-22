package com.app.service.impl;

import com.app.dto.response.MiResponses.InsightActionDto;
import com.app.dto.response.MiResponses.InsightDto;
import com.app.model.Insight;
import com.app.repository.InsightRepository;
import com.app.service.AutonomyPolicy;
import com.app.service.InsightService;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class InsightServiceImpl implements InsightService {

  private static final String PLACEMENT_HOME = "HOME";

  private final InsightRepository insights;
  private final AutonomyPolicy autonomyPolicy;
  private final MessageSource messages;
  private final ObjectMapper objectMapper;

  public InsightServiceImpl(
      InsightRepository insights,
      AutonomyPolicy autonomyPolicy,
      MessageSource messages,
      ObjectMapper objectMapper) {
    this.insights = insights;
    this.autonomyPolicy = autonomyPolicy;
    this.messages = messages;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public InsightDto forPlacement(UUID customerId, String placement, Locale locale) {
    // The home banner is the one placement the customer can switch off (screen 09).
    if (PLACEMENT_HOME.equals(placement)
        && !autonomyPolicy.settings(customerId).isPermHomeInsights()) {
      return null;
    }
    Optional<Insight> insight =
        insights.findFirstByCustomerIdAndPlacementAndValidUntilAfterOrderByCreatedAtDesc(
            customerId, placement, Instant.now());
    return insight.map(row -> toDto(row, locale)).orElse(null);
  }

  private InsightDto toDto(Insight insight, Locale locale) {
    Map<String, Object> args =
        objectMapper.readValue(insight.getArgs(), new TypeReference<Map<String, Object>>() {});
    Object[] ordered =
        args.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .toArray();
    InsightActionDto action =
        insight.getDeepLink() == null
            ? null
            : new InsightActionDto(
                messages.getMessage(
                    insight.getActionKey() == null ? "mi.insight.action" : insight.getActionKey(),
                    null,
                    "",
                    locale),
                insight.getDeepLink());
    return new InsightDto(
        insight.getPlacement(),
        messages.getMessage(insight.getTextKey(), ordered, insight.getTextKey(), locale),
        insight.getPrompt(),
        action,
        insight.getTone());
  }
}
