package com.app.service.impl;

import com.app.constant.ErrorCode;
import com.app.constant.MiConstants;
import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.LocalizedText;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.dto.internal.MiInternal.ToolSpec;
import com.app.exception.BusinessException;
import com.app.service.agent.AgentRegistry;
import com.app.service.tool.MiTool;
import com.app.service.tool.ToolRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ToolRegistryImpl implements ToolRegistry {

  private static final Logger log = LoggerFactory.getLogger(ToolRegistryImpl.class);

  private final Map<String, MiTool> tools;
  private final AgentRegistry registry;
  private final boolean chatPayEnabled;

  public ToolRegistryImpl(
      List<MiTool> beans,
      AgentRegistry registry,
      @Value("${app.mi.chatpay.enabled}") boolean chatPayEnabled) {
    this.tools = beans.stream().collect(Collectors.toMap(MiTool::code, Function.identity()));
    this.registry = registry;
    this.chatPayEnabled = chatPayEnabled;
  }

  @Override
  public List<ToolSpec> available(AgentSpec agent, boolean toolsDisabled) {
    if (toolsDisabled) {
      // An injection-suspected turn still gets an answer, just never one with side effects.
      return List.of();
    }
    List<String> allowed = agent.toolCodes() == null ? List.of() : agent.toolCodes();
    return allowed.stream()
        .filter(tools::containsKey)
        .map(code -> registry.tool(code).orElseGet(() -> describe(code)))
        .filter(ToolSpec::enabled)
        .filter(spec -> chatPayEnabled || !spec.mutating())
        // Don't advertise a capability the agent may not use: offering it and then refusing wastes
        // a turn and invites the model to keep retrying.
        .filter(spec -> !ToolCodes.PROPOSE_NEW_CARD.equals(spec.code()) || issuePermitted(agent))
        .toList();
  }

  private boolean issuePermitted(AgentSpec agent) {
    return agent.guardrails() != null && agent.guardrails().allowIssue();
  }

  @Override
  public ToolResult invoke(
      AgentSpec agent, String code, MiTool.Context context, Map<String, Object> args) {

    List<String> allowed = agent.toolCodes() == null ? List.of() : agent.toolCodes();
    if (!allowed.contains(code)) {
      // The model asked for something outside the CMS allow-list. Refuse and record it.
      log.warn("TOOL_DENIED agent={} tool={}", agent.code(), code);
      throw new BusinessException(ErrorCode.GUARDRAIL, Map.of("tool", code, "reason",
          "TOOL_DENIED"));
    }
    MiTool tool = tools.get(code);
    if (tool == null) {
      throw new BusinessException(ErrorCode.CANNOT_UNDERSTAND, Map.of("tool", code));
    }
    if (tool.mutating() && !chatPayEnabled) {
      throw new BusinessException(ErrorCode.CHAT_PAY_DISABLED);
    }
    // Opening a product is a distinct right from moving money, so it is gated separately and
    // enforced here rather than inside the tool: this is the only place that holds both the tool
    // code and the CMS agent definition (design 07 §3.2).
    if (ToolCodes.PROPOSE_NEW_CARD.equals(code) && !issuePermitted(agent)) {
      log.warn("ISSUE_DENIED agent={} tool={}", agent.code(), code);
      throw new BusinessException(
          ErrorCode.ISSUE_NOT_ALLOWED, Map.of("tool", code, "agent", agent.code()));
    }
    long started = System.nanoTime();
    try {
      return tool.execute(context, withScreenEntity(context, args));
    } catch (BusinessException e) {
      return new ToolResult(
          code, false, null, e.getErrorCode().code, (System.nanoTime() - started) / 1_000_000);
    }
  }

  /**
   * Fills the entity argument from the screen the customer is on, so "trả trước 20 triệu" on the
   * loan detail screen does not have to ask which loan (M3.12).
   *
   * <p>Two deliberate limits. The model wins: an argument it supplied is never overwritten, because
   * the customer may well be asking about something other than what is on screen. And the id is
   * only a hint — it arrives from the client, so the owning domain service still verifies the entity
   * belongs to this customer, exactly as it does for any other id.
   */
  private Map<String, Object> withScreenEntity(MiTool.Context context, Map<String, Object> args) {
    Map<String, Object> given = args == null ? Map.of() : args;
    MiTool.ScreenContext screen = context.screen();
    if (screen == null || !screen.hasEntity()) {
      return given;
    }
    String argName = MiConstants.ENTITY_ARG_BY_TYPE.get(screen.entityType());
    if (argName == null || given.get(argName) != null) {
      return given;
    }
    Map<String, Object> merged = new java.util.HashMap<>(given);
    merged.put(argName, screen.entityId().toString());
    log.debug(
        "screen context filled {}={} from {}", argName, screen.entityId(), screen.screen());
    return merged;
  }

  /** Fallback description when the CMS has not published a row for a bean that exists. */
  private ToolSpec describe(String code) {
    return new ToolSpec(
        code,
        new LocalizedText(code, code),
        code,
        null,
        code,
        Map.of(),
        ToolCodes.MUTATING.contains(code),
        ToolCodes.STEP_UP_SCOPES.get(code),
        true,
        List.of());
  }

  private Optional<MiTool> find(String code) {
    return Optional.ofNullable(tools.get(code));
  }
}
