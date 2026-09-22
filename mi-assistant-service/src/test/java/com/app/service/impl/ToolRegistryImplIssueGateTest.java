package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.constant.AgentCodes;
import com.app.constant.ErrorCode;
import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.Guardrails;
import com.app.dto.internal.MiInternal.LocalizedText;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.dto.internal.MiInternal.ToolSpec;
import com.app.exception.BusinessException;
import com.app.service.agent.AgentRegistry;
import com.app.service.tool.MiTool;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Opening a card is a right an agent has to be granted (07 §3.2). These tests cover the two places
 * that enforce it: the catalogue the model is shown, and the call itself.
 */
class ToolRegistryImplIssueGateTest {

  private static final UUID CUSTOMER = UUID.randomUUID();

  /** A stand-in for the real tool: the gate must stop the call before any of this would run. */
  private static class RecordingTool implements MiTool {

    private final String code;
    private boolean called;

    RecordingTool(String code) {
      this.code = code;
    }

    @Override
    public String code() {
      return code;
    }

    @Override
    public boolean mutating() {
      return true;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      called = true;
      return new ToolResult(code, true, Map.of(), null, 1);
    }
  }

  private static AgentSpec cardAgent(boolean allowIssue) {
    return new AgentSpec(
        AgentCodes.CARD,
        new LocalizedText("Mi", "Mi"),
        AgentCodes.DOMAIN_CARD,
        true,
        1,
        new LocalizedText("", ""),
        null,
        BigDecimal.valueOf(0.2),
        List.of(ToolCodes.PROPOSE_NEW_CARD, ToolCodes.UNLOCK_CARD),
        List.of(),
        new Guardrails(List.of(), "mi.reply.guardrail.offTopic", null, true, 4, allowIssue),
        List.of(),
        null,
        "test");
  }

  private static MiTool.Context context() {
    return new MiTool.Context(CUSTOMER, UUID.randomUUID(), UUID.randomUUID(), Locale.ENGLISH, null);
  }

  private ToolRegistryImpl registry(List<MiTool> tools, boolean chatPayEnabled) {
    AgentRegistry agents = Mockito.mock(AgentRegistry.class);
    // No CMS row for these codes, so the registry falls back to its own description.
    Mockito.when(agents.tool(Mockito.anyString())).thenReturn(Optional.empty());
    return new ToolRegistryImpl(tools, agents, chatPayEnabled);
  }

  @Test
  @DisplayName("Without allowIssue the card-opening tool is refused with MI-015")
  void refusesIssueWhenNotPermitted() {
    RecordingTool tool = new RecordingTool(ToolCodes.PROPOSE_NEW_CARD);
    ToolRegistryImpl registry = registry(List.of(tool), true);

    assertThatThrownBy(
            () ->
                registry.invoke(
                    cardAgent(false), ToolCodes.PROPOSE_NEW_CARD, context(), Map.of()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.ISSUE_NOT_ALLOWED);

    // The refusal has to happen before the tool runs, or a card would already be applied for.
    assertThat(tool.called).isFalse();
  }

  @Test
  @DisplayName("With allowIssue the same call goes through")
  void allowsIssueWhenPermitted() {
    RecordingTool tool = new RecordingTool(ToolCodes.PROPOSE_NEW_CARD);
    ToolRegistryImpl registry = registry(List.of(tool), true);

    ToolResult result =
        registry.invoke(cardAgent(true), ToolCodes.PROPOSE_NEW_CARD, context(), Map.of());

    assertThat(result.ok()).isTrue();
    assertThat(tool.called).isTrue();
  }

  @Test
  @DisplayName("A tool the agent may not use is never advertised to the model")
  void hidesIssueToolFromCatalogue() {
    ToolRegistryImpl registry =
        registry(
            List.of(
                new RecordingTool(ToolCodes.PROPOSE_NEW_CARD),
                new RecordingTool(ToolCodes.UNLOCK_CARD)),
            true);

    List<String> withoutRight =
        registry.available(cardAgent(false), false).stream().map(ToolSpec::code).toList();
    List<String> withRight =
        registry.available(cardAgent(true), false).stream().map(ToolSpec::code).toList();

    assertThat(withoutRight).containsExactly(ToolCodes.UNLOCK_CARD);
    assertThat(withRight).contains(ToolCodes.PROPOSE_NEW_CARD, ToolCodes.UNLOCK_CARD);
  }

  @Test
  @DisplayName("Chat Pay off disables card opening and unlocking, permission or not")
  void chatPayOffDisablesMutatingCardTools() {
    RecordingTool issue = new RecordingTool(ToolCodes.PROPOSE_NEW_CARD);
    RecordingTool unlock = new RecordingTool(ToolCodes.UNLOCK_CARD);
    ToolRegistryImpl registry = registry(List.of(issue, unlock), false);

    assertThat(registry.available(cardAgent(true), false)).isEmpty();

    assertThatThrownBy(
            () -> registry.invoke(cardAgent(true), ToolCodes.UNLOCK_CARD, context(), Map.of()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CHAT_PAY_DISABLED);
    assertThat(unlock.called).isFalse();
  }

  @Test
  @DisplayName("An injection-suspected turn is offered no tools at all")
  void toolsDisabledYieldsEmptyCatalogue() {
    ToolRegistryImpl registry =
        registry(List.of(new RecordingTool(ToolCodes.UNLOCK_CARD)), true);

    assertThat(registry.available(cardAgent(true), true)).isEmpty();
  }

  @Test
  @DisplayName("A tool outside the agent's CMS allow-list is denied as a guardrail breach")
  void deniesToolOutsideAllowList() {
    ToolRegistryImpl registry =
        registry(List.of(new RecordingTool(ToolCodes.PROPOSE_TRANSFER)), true);

    assertThatThrownBy(
            () ->
                registry.invoke(
                    cardAgent(true), ToolCodes.PROPOSE_TRANSFER, context(), Map.of()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.GUARDRAIL);
  }
}
