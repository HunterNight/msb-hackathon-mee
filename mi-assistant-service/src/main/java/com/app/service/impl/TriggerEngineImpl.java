package com.app.service.impl;

import com.app.constant.MiConstants;
import com.app.dto.internal.MiInternal.TriggerRule;
import com.app.model.Insight;
import com.app.model.Nudge;
import com.app.repository.InsightRepository;
import com.app.repository.NudgeRepository;
import com.app.service.EventBus;
import com.app.service.agent.AgentRegistry;
import com.app.service.memory.MemoryService;
import com.app.service.proactive.TriggerEngine;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Rules are data, not code: a condition is a small expression over the snapshot's exact slice
 * ({@code idleBalance > 5000000}), so a new nudge is a CMS edit.
 */
@Service
public class TriggerEngineImpl implements TriggerEngine {

  private static final Logger log = LoggerFactory.getLogger(TriggerEngineImpl.class);
  private static final LocalTime QUIET_FROM = LocalTime.of(22, 0);
  private static final LocalTime QUIET_TO = LocalTime.of(7, 0);

  private final AgentRegistry registry;
  private final MemoryService memoryService;
  private final NudgeRepository nudges;
  private final InsightRepository insights;
  private final EventBus eventBus;
  private final ObjectMapper objectMapper;

  public TriggerEngineImpl(
      AgentRegistry registry,
      MemoryService memoryService,
      NudgeRepository nudges,
      InsightRepository insights,
      EventBus eventBus,
      ObjectMapper objectMapper) {
    this.registry = registry;
    this.memoryService = memoryService;
    this.nudges = nudges;
    this.insights = insights;
    this.eventBus = eventBus;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public int evaluate(UUID customerId) {
    Map<String, Object> snapshot = memoryService.triggerSnapshot(customerId);
    if (snapshot.isEmpty()) {
      return 0;
    }
    int fired = 0;
    for (TriggerRule rule : registry.triggers()) {
      if (!fires(rule, snapshot, customerId)) {
        continue;
      }
      emit(customerId, rule, snapshot);
      fired++;
    }
    return fired;
  }

  @Override
  @Transactional
  public boolean dismiss(UUID customerId, UUID nudgeId) {
    Optional<Nudge> nudge = nudges.findByIdAndCustomerId(nudgeId, customerId);
    if (nudge.isEmpty()) {
      return false;
    }
    Nudge row = nudge.get();
    row.setDismissed(true);
    row.setDismissedAt(Instant.now());
    nudges.save(row);
    // memory-hook counts these: three dismissals become a stated preference.
    eventBus.publish(
        "msb.mi.nudgeDismissed.v1",
        customerId,
        objectMapper.writeValueAsString(
            Map.of("customerId", customerId, "triggerCode", row.getTriggerCode())));
    return true;
  }

  private boolean fires(TriggerRule rule, Map<String, Object> snapshot, UUID customerId) {
    if (!rule.enabled() || inQuietHours()) {
      return false;
    }
    int cooldown = rule.cooldownHours() <= 0 ? 24 : rule.cooldownHours();
    if (nudges.existsByCustomerIdAndTriggerCodeAndCreatedAtAfter(
        customerId, rule.code(), Instant.now().minus(Duration.ofHours(cooldown)))) {
      return false;
    }
    int dailyCap = rule.dailyCap() <= 0 ? 1 : rule.dailyCap();
    if (nudges.countByCustomerIdAndCreatedAtAfter(customerId, Instant.now().minus(Duration.ofDays(1)))
        >= dailyCap) {
      return false;
    }
    return matches(rule.conditionExpr(), snapshot);
  }

  /**
   * A deliberately tiny expression language — {@code field op number} — because a trigger
   * condition is authored in a back office and must never be able to run arbitrary code.
   */
  private boolean matches(String condition, Map<String, Object> snapshot) {
    if (condition == null || condition.isBlank()) {
      return true;
    }
    String[] parts = condition.trim().split("\\s+");
    if (parts.length != 3) {
      log.warn("trigger condition '{}' is not in the form 'field op value'", condition);
      return false;
    }
    Object raw = snapshot.get(parts[0]);
    if (raw == null) {
      return false;
    }
    BigDecimal left;
    BigDecimal right;
    try {
      left = new BigDecimal(raw.toString());
      right = new BigDecimal(parts[2]);
    } catch (NumberFormatException e) {
      return false;
    }
    return switch (parts[1]) {
      case ">" -> left.compareTo(right) > 0;
      case ">=" -> left.compareTo(right) >= 0;
      case "<" -> left.compareTo(right) < 0;
      case "<=" -> left.compareTo(right) <= 0;
      case "==" -> left.compareTo(right) == 0;
      default -> false;
    };
  }

  private void emit(UUID customerId, TriggerRule rule, Map<String, Object> snapshot) {
    // The CMS names the agent, not the surface; the agent's domain decides where the nudge lands.
    String placement = placementFor(rule.agentCode());

    Nudge nudge = new Nudge();
    nudge.setCustomerId(customerId);
    nudge.setTriggerCode(rule.code());
    nudge.setPlacement(placement);
    nudge.setTextKey(rule.templateCode());
    nudge.setExplainKey(rule.code() + ".explain");
    nudge.setArgs(objectMapper.writeValueAsString(snapshot));
    nudge.setExpiresAt(Instant.now().plus(MiConstants.NUDGE_TTL));
    nudges.save(nudge);

    Insight insight = new Insight();
    insight.setCustomerId(customerId);
    insight.setPlacement(placement);
    insight.setTextKey(rule.templateCode());
    insight.setArgs(objectMapper.writeValueAsString(snapshot));
    insight.setTriggerCode(rule.code());
    insight.setValidUntil(Instant.now().plus(MiConstants.NUDGE_TTL));
    insights.save(insight);
  }

  private String placementFor(String agentCode) {
    return switch (agentCode == null ? "" : agentCode) {
      case com.app.constant.AgentCodes.CARD -> "CARD_WALLET";
      case com.app.constant.AgentCodes.SAVING -> "SAVING_OVERVIEW";
      case com.app.constant.AgentCodes.LOAN -> "LENDING_OVERVIEW";
      case com.app.constant.AgentCodes.PAYMENT -> "TRANSFER_REVIEW";
      default -> "HOME";
    };
  }

  private boolean inQuietHours() {
    LocalTime now = LocalTime.now(com.app.constant.AppConstants.USER_ZONE);
    return now.isAfter(QUIET_FROM) || now.isBefore(QUIET_TO);
  }
}
