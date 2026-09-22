package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.app.constant.AgentCodes;
import com.app.constant.MiConstants;
import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.Guardrails;
import com.app.dto.internal.MiInternal.LocalizedText;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.service.agent.AgentRegistry;
import com.app.service.tool.MiTool;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

/**
 * Screen context resolution (M3.12): the entity the customer is looking at fills the argument the
 * model left out, so "trả trước 20 triệu" on the loan detail screen does not have to ask which loan.
 */
class ToolRegistryScreenContextTest {

  private static final UUID CUSTOMER = UUID.randomUUID();

  /** Captures the arguments the registry actually passed, which is what these tests assert on. */
  private static class ArgCapturingTool implements MiTool {

    private final String code;
    private Map<String, Object> received;

    ArgCapturingTool(String code) {
      this.code = code;
    }

    @Override
    public String code() {
      return code;
    }

    @Override
    public boolean mutating() {
      return false;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      received = args;
      return new ToolResult(code, true, Map.of(), null, 1);
    }
  }

  private static AgentSpec agent(String... toolCodes) {
    return new AgentSpec(
        AgentCodes.LOAN,
        new LocalizedText("Mi", "Mi"),
        AgentCodes.DOMAIN_LOAN,
        true,
        1,
        new LocalizedText("", ""),
        null,
        BigDecimal.valueOf(0.2),
        List.of(toolCodes),
        List.of(),
        new Guardrails(List.of(), "k", null, true, 4, false),
        List.of(),
        null,
        "test");
  }

  private static MiTool.Context contextOn(String screen, String entityType, UUID entityId) {
    return new MiTool.Context(
        CUSTOMER,
        UUID.randomUUID(),
        UUID.randomUUID(),
        Locale.ENGLISH,
        null,
        entityType == null ? null : new MiTool.ScreenContext(screen, entityType, entityId));
  }

  private ToolRegistryImpl registry(MiTool tool) {
    AgentRegistry agents = Mockito.mock(AgentRegistry.class);
    when(agents.tool(Mockito.anyString())).thenReturn(Optional.empty());
    return new ToolRegistryImpl(List.of(tool), agents, true);
  }

  @ParameterizedTest(name = "{0} on screen fills {1}")
  @CsvSource({
    "LOAN, loanId",
    "CARD, cardId",
    "GOAL, goalId",
    "DEPOSIT, depositId",
    "ACCOUNT, accountId",
    "BILL, billId",
    "BENEFICIARY, beneficiaryId"
  })
  @DisplayName("Each entity type fills the argument its tools actually read")
  void fillsArgumentPerEntityType(String entityType, String expectedArg) {
    ArgCapturingTool tool = new ArgCapturingTool(ToolCodes.QUOTE_PREPAYMENT);
    UUID entityId = UUID.randomUUID();

    registry(tool)
        .invoke(
            agent(ToolCodes.QUOTE_PREPAYMENT),
            ToolCodes.QUOTE_PREPAYMENT,
            contextOn("some.screen", entityType, entityId),
            Map.of("amount", 20_000_000));

    assertThat(tool.received).containsEntry(expectedArg, entityId.toString());
    // The model's own argument survives untouched.
    assertThat(tool.received).containsEntry("amount", 20_000_000);
    assertThat(MiConstants.ENTITY_ARG_BY_TYPE).containsEntry(entityType, expectedArg);
  }

  @Test
  @DisplayName("The model wins: an argument it supplied is never overwritten by the screen")
  void doesNotOverrideTheModel() {
    ArgCapturingTool tool = new ArgCapturingTool(ToolCodes.QUOTE_PREPAYMENT);
    UUID onScreen = UUID.randomUUID();
    UUID askedFor = UUID.randomUUID();

    registry(tool)
        .invoke(
            agent(ToolCodes.QUOTE_PREPAYMENT),
            ToolCodes.QUOTE_PREPAYMENT,
            contextOn("loan.detail", "LOAN", onScreen),
            Map.of("loanId", askedFor.toString()));

    // The customer may be asking about a different loan than the one on screen; the screen is only
    // a default, never an override.
    assertThat(tool.received).containsEntry("loanId", askedFor.toString());
    assertThat(tool.received.get("loanId")).isNotEqualTo(onScreen.toString());
  }

  @Test
  @DisplayName("No screen context leaves the arguments exactly as the model produced them")
  void withoutScreenContextArgsAreUnchanged() {
    ArgCapturingTool tool = new ArgCapturingTool(ToolCodes.QUOTE_PREPAYMENT);

    registry(tool)
        .invoke(
            agent(ToolCodes.QUOTE_PREPAYMENT),
            ToolCodes.QUOTE_PREPAYMENT,
            contextOn(null, null, null),
            Map.of("amount", 5_000_000));

    assertThat(tool.received).containsExactlyInAnyOrderEntriesOf(Map.of("amount", 5_000_000));
  }

  @Test
  @DisplayName("A screen with no entity adds nothing")
  void screenWithoutEntityAddsNothing() {
    ArgCapturingTool tool = new ArgCapturingTool(ToolCodes.GET_LOAN_OVERVIEW);

    registry(tool)
        .invoke(
            agent(ToolCodes.GET_LOAN_OVERVIEW),
            ToolCodes.GET_LOAN_OVERVIEW,
            new MiTool.Context(
                CUSTOMER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Locale.ENGLISH,
                null,
                new MiTool.ScreenContext("mi.chat", null, null)),
            Map.of());

    assertThat(tool.received).isEmpty();
  }

  @Test
  @DisplayName("An entity type with no mapped argument is ignored rather than guessed")
  void unknownEntityTypeIsIgnored() {
    ArgCapturingTool tool = new ArgCapturingTool(ToolCodes.GET_LOAN_OVERVIEW);

    registry(tool)
        .invoke(
            agent(ToolCodes.GET_LOAN_OVERVIEW),
            ToolCodes.GET_LOAN_OVERVIEW,
            contextOn("future.screen", "SOME_FUTURE_ENTITY", UUID.randomUUID()),
            Map.of());

    assertThat(tool.received).isEmpty();
  }

  @Test
  @DisplayName("Null arguments from the model are tolerated and still get the screen entity")
  void nullArgsStillReceiveTheEntity() {
    ArgCapturingTool tool = new ArgCapturingTool(ToolCodes.GET_STATEMENT);
    UUID cardId = UUID.randomUUID();

    registry(tool)
        .invoke(
            agent(ToolCodes.GET_STATEMENT),
            ToolCodes.GET_STATEMENT,
            contextOn("card.detail", "CARD", cardId),
            null);

    assertThat(tool.received).containsEntry("cardId", cardId.toString());
  }

  @Test
  @DisplayName("The caller's argument map is not mutated")
  void doesNotMutateCallerArgs() {
    ArgCapturingTool tool = new ArgCapturingTool(ToolCodes.QUOTE_PREPAYMENT);
    Map<String, Object> original = new HashMap<>();
    original.put("amount", 1_000_000);

    registry(tool)
        .invoke(
            agent(ToolCodes.QUOTE_PREPAYMENT),
            ToolCodes.QUOTE_PREPAYMENT,
            contextOn("loan.detail", "LOAN", UUID.randomUUID()),
            original);

    // The model reply's args are reused for logging and eval; filling them in place would make the
    // trace disagree with what the model actually asked for.
    assertThat(original).containsOnlyKeys("amount");
  }
}
