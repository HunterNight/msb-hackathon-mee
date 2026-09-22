package com.app.service.impl;

import com.app.constant.AgentCodes;
import com.app.constant.ErrorCode;
import com.app.constant.MiConstants;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.QuickPrompt;
import com.app.dto.request.MiRequests.ConsentsRequest;
import com.app.dto.request.MiRequests.PermissionsPatch;
import com.app.dto.request.MiRequests.UpdateSettingsRequest;
import com.app.dto.response.MiResponses.AgentSummaryDto;
import com.app.dto.response.MiResponses.ConsentsResponse;
import com.app.dto.response.MiResponses.DecideResponse;
import com.app.dto.response.MiResponses.ExplainInput;
import com.app.dto.response.MiResponses.ExplainModel;
import com.app.dto.response.MiResponses.ExplainPolicy;
import com.app.dto.response.MiResponses.ExplainResponse;
import com.app.dto.response.MiResponses.NudgeDto;
import com.app.dto.response.MiResponses.PermissionsDto;
import com.app.dto.response.MiResponses.SettingsResponse;
import com.app.dto.response.MiResponses.SuggestionDto;
import com.app.exception.BusinessException;
import com.app.model.CustomerAutonomy;
import com.app.model.DecisionLog;
import com.app.repository.CustomerAutonomyRepository;
import com.app.repository.DecisionLogRepository;
import com.app.repository.NudgeRepository;
import com.app.service.AutonomyPolicy;
import com.app.service.MiSettingsService;
import com.app.service.agent.AgentRegistry;
import com.app.service.memory.MemoryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class MiSettingsServiceImpl implements MiSettingsService {

  private final CustomerAutonomyRepository autonomies;
  private final AutonomyPolicy autonomyPolicy;
  private final AgentRegistry registry;
  private final NudgeRepository nudges;
  private final DecisionLogRepository decisions;
  private final MemoryService memoryService;
  private final MessageSource messages;
  private final ObjectMapper objectMapper;
  private final boolean chatPayEnabled;

  public MiSettingsServiceImpl(
      CustomerAutonomyRepository autonomies,
      AutonomyPolicy autonomyPolicy,
      AgentRegistry registry,
      NudgeRepository nudges,
      DecisionLogRepository decisions,
      MemoryService memoryService,
      MessageSource messages,
      ObjectMapper objectMapper,
      @Value("${app.mi.chatpay.enabled}") boolean chatPayEnabled) {
    this.autonomies = autonomies;
    this.autonomyPolicy = autonomyPolicy;
    this.registry = registry;
    this.nudges = nudges;
    this.decisions = decisions;
    this.memoryService = memoryService;
    this.messages = messages;
    this.objectMapper = objectMapper;
    this.chatPayEnabled = chatPayEnabled;
  }

  @Override
  @Transactional
  public SettingsResponse settings(UUID customerId) {
    return toDto(autonomyPolicy.settings(customerId));
  }

  @Override
  @Transactional
  public SettingsResponse update(UUID customerId, UpdateSettingsRequest request) {
    CustomerAutonomy autonomy = autonomyPolicy.settings(customerId);

    if (request.autoLimit() != null) {
      // The slider has five stops; anything else is a client that drifted from the contract.
      boolean allowed =
          MiConstants.AUTO_LIMIT_STEPS.stream()
              .anyMatch(step -> step.compareTo(request.autoLimit()) == 0);
      if (!allowed) {
        throw new BusinessException(
            ErrorCode.INVALID_LIMIT, Map.of("allowed", MiConstants.AUTO_LIMIT_STEPS));
      }
      autonomy.setAutoLimit(request.autoLimit());
    }
    PermissionsPatch patch = request.permissions();
    if (patch != null) {
      if (patch.recurringBills() != null) {
        autonomy.setPermRecurringBills(patch.recurringBills());
      }
      if (patch.savedRecipients() != null) {
        autonomy.setPermSavedRecipients(patch.savedRecipients());
      }
      if (patch.autoSaving() != null) {
        autonomy.setPermAutoSaving(patch.autoSaving());
      }
      if (patch.homeInsights() != null) {
        autonomy.setPermHomeInsights(patch.homeInsights());
      }
    }
    autonomy.setUpdatedAt(Instant.now());
    return toDto(autonomies.save(autonomy));
  }

  @Override
  @Transactional
  public SettingsResponse pause(UUID customerId, boolean paused) {
    CustomerAutonomy autonomy = autonomyPolicy.settings(customerId);
    autonomy.setPaused(paused);
    autonomy.setUpdatedAt(Instant.now());
    return toDto(autonomies.save(autonomy));
  }

  @Override
  @Transactional
  public ConsentsResponse consents(UUID customerId) {
    CustomerAutonomy autonomy = autonomyPolicy.settings(customerId);
    // memory-hook is the record of truth for the long-term flag; Mi only mirrors it.
    boolean longTerm = memoryService.consent(customerId);
    return new ConsentsResponse(
        autonomy.isProactive(),
        longTerm,
        autonomy.isImproveModels(),
        Map.of("policy", autonomy.getPolicyVersion()));
  }

  @Override
  @Transactional
  public ConsentsResponse updateConsents(UUID customerId, ConsentsRequest request) {
    CustomerAutonomy autonomy = autonomyPolicy.settings(customerId);
    if (request.proactive() != null) {
      autonomy.setProactive(request.proactive());
    }
    if (request.improveModels() != null) {
      autonomy.setImproveModels(request.improveModels());
    }
    boolean longTerm =
        request.memoryLongTerm() == null
            ? memoryService.consent(customerId)
            : memoryService.setConsent(customerId, request.memoryLongTerm());
    autonomy.setMemoryLongTerm(longTerm);
    autonomy.setUpdatedAt(Instant.now());
    autonomies.save(autonomy);
    return new ConsentsResponse(
        autonomy.isProactive(),
        longTerm,
        autonomy.isImproveModels(),
        Map.of("policy", autonomy.getPolicyVersion()));
  }

  @Override
  public List<AgentSummaryDto> agents(Locale locale) {
    return registry.enabledAgents().stream()
        .filter(agent -> !AgentCodes.ROUTER.equals(agent.code()))
        .map(agent -> toSummary(agent, locale))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<NudgeDto> nudges(UUID customerId, Locale locale) {
    return nudges
        .findByCustomerIdAndDismissedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            customerId, Instant.now())
        .stream()
        .map(
            nudge ->
                new NudgeDto(
                    nudge.getId(),
                    nudge.getPlacement(),
                    messages.getMessage(
                        nudge.getTextKey(), args(nudge.getArgs()), nudge.getTextKey(), locale),
                    messages.getMessage(
                        nudge.getExplainKey(), null, nudge.getExplainKey(), locale),
                    nudge.getPrompt(),
                    nudge.getDeepLink(),
                    nudge.getCreatedAt(),
                    nudge.getExpiresAt()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ExplainResponse explain(UUID customerId, UUID decisionId, Locale locale) {
    DecisionLog decision =
        decisions
            .findByIdAndCustomerId(decisionId, customerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    List<ExplainInput> inputs =
        objectMapper.readValue(
            decision.getInputs(), new TypeReference<List<ExplainInput>>() {});
    return new ExplainResponse(
        decision.getKind(),
        messages.getMessage(decision.getReasonKey(), null, decision.getReasonKey(), locale),
        inputs,
        new ExplainPolicy(decision.getPolicyName(), decision.getPolicyVersion()),
        new ExplainModel(decision.getModelProfile(), decision.getModelVersion()),
        decision.getCreatedAt());
  }

  @Override
  @Transactional
  public DecideResponse decide(
      UUID customerId, String type, BigDecimal amount, Boolean beneficiarySaved) {
    CustomerAutonomy autonomy = autonomyPolicy.settings(customerId);
    AutonomyPolicy.Decision decision =
        autonomyPolicy.decide(
            autonomy, type, amount, beneficiarySaved != null && beneficiarySaved);
    return new DecideResponse(decision.decision(), decision.reason(), autonomy.getAutoLimit());
  }

  private AgentSummaryDto toSummary(AgentSpec agent, Locale locale) {
    List<SuggestionDto> prompts =
        agent.quickPrompts() == null
            ? List.of()
            : agent.quickPrompts().stream()
                .filter(QuickPrompt::enabled)
                .sorted(java.util.Comparator.comparingInt(QuickPrompt::sort))
                .map(QuickPrompt::text)
                .map(text -> text.forLocale(locale))
                .map(text -> new SuggestionDto(text, text, "sparkle"))
                .toList();
    return new AgentSummaryDto(
        agent.code(),
        // The UI always says "Mi"; the agent name is for the CMS dashboard, not the customer.
        agent.name() == null ? agent.code() : agent.name().forLocale(locale),
        agent.domain(),
        prompts);
  }

  private SettingsResponse toDto(CustomerAutonomy autonomy) {
    return new SettingsResponse(
        new PermissionsDto(
            autonomy.isPermRecurringBills(),
            autonomy.isPermSavedRecipients(),
            autonomy.isPermAutoSaving(),
            autonomy.isPermHomeInsights()),
        autonomy.getAutoLimit(),
        MiConstants.AUTO_LIMIT_STEPS,
        autonomy.isPaused(),
        chatPayEnabled);
  }

  private Object[] args(String json) {
    Map<String, Object> map =
        objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    return map.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(Map.Entry::getValue)
        .toArray();
  }
}
