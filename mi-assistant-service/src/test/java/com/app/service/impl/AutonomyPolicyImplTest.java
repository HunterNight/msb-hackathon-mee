package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.constant.MiConstants;
import com.app.model.CustomerAutonomy;
import com.app.repository.CustomerAutonomyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

/**
 * The autonomy truth table (design §3.2 and 07 §3.2). These assertions are the only thing standing
 * between "Mi acts within limits the customer set" and "Mi acts".
 */
class AutonomyPolicyImplTest {

  private final AutonomyPolicyImpl policy =
      new AutonomyPolicyImpl(Mockito.mock(CustomerAutonomyRepository.class));

  private static CustomerAutonomy autonomy(
      boolean bills, boolean recipients, boolean saving, boolean paused, String limit) {
    CustomerAutonomy autonomy = new CustomerAutonomy();
    autonomy.setPermRecurringBills(bills);
    autonomy.setPermSavedRecipients(recipients);
    autonomy.setPermAutoSaving(saving);
    autonomy.setPaused(paused);
    autonomy.setAutoLimit(new BigDecimal(limit));
    return autonomy;
  }

  /** Everything on, a generous limit: the most permissive customer the settings screen allows. */
  private static CustomerAutonomy allPermissionsOn() {
    return autonomy(true, true, true, false, "10000000");
  }

  // ── CARD_ISSUE: never automatic, under any configuration ────────────────────

  /**
   * The point of this case list is that no combination of permissions or limits reaches AUTO.
   * Opening a card creates a contractual obligation and a bureau footprint, and the auto limit is a
   * control over money movement, which a card application is not.
   */
  static Stream<Arguments> everyAutonomyShape() {
    return Stream.of(
        Arguments.of("all permissions on", allPermissionsOn()),
        Arguments.of("all permissions off", autonomy(false, false, false, false, "10000000")),
        Arguments.of("paused", autonomy(true, true, true, true, "10000000")),
        Arguments.of("zero limit", autonomy(true, true, true, false, "0")),
        Arguments.of("huge limit", autonomy(true, true, true, false, "999999999999")));
  }

  @ParameterizedTest(name = "CARD_ISSUE is never AUTO: {0}")
  @MethodSource("everyAutonomyShape")
  @DisplayName("A card application always needs the customer's confirmation")
  void cardIssueNeverAuto(String shape, CustomerAutonomy autonomy) {
    // Tried with no amount (how the tool actually builds it), with zero, and with a large number,
    // because an amount-based rule must not be what decides this.
    for (BigDecimal amount : List.of(BigDecimal.ZERO, new BigDecimal("100000000"))) {
      var decision = policy.decide(autonomy, MiConstants.TYPE_CARD_ISSUE, amount, true);
      assertThat(decision.auto())
          .as("CARD_ISSUE with amount %s and %s", amount, shape)
          .isFalse();
      assertThat(decision.decision()).isEqualTo(MiConstants.DECISION_CONFIRM);
    }
    var withoutAmount = policy.decide(autonomy, MiConstants.TYPE_CARD_ISSUE, null, true);
    assertThat(withoutAmount.auto()).as("CARD_ISSUE with no amount and %s", shape).isFalse();
    assertThat(withoutAmount.decision()).isEqualTo(MiConstants.DECISION_CONFIRM);
  }

  @Test
  @DisplayName("CARD_ISSUE reports product consent as the reason, not a limit or permission")
  void cardIssueReasonIsProductConsent() {
    // The reason travels to the activity log and the UI, so a customer with everything switched on
    // is told why Mi still asked: it is the product, not their settings.
    var decision =
        policy.decide(allPermissionsOn(), MiConstants.TYPE_CARD_ISSUE, null, true);

    assertThat(decision.reason()).isEqualTo(MiConstants.REASON_PRODUCT_CONSENT);
  }

  @Test
  @DisplayName("A paused Mi reports being paused even for a card application")
  void pausedTakesPrecedenceInReporting() {
    var decision =
        policy.decide(
            autonomy(true, true, true, true, "10000000"),
            MiConstants.TYPE_CARD_ISSUE,
            null,
            true);

    assertThat(decision.auto()).isFalse();
    assertThat(decision.reason()).isEqualTo(MiConstants.REASON_PAUSED);
  }

  // ── the pre-existing table, so this upgrade cannot have loosened it ─────────

  @Test
  @DisplayName("SET_BUDGET stays automatic: it moves no money and the customer can undo it")
  void setBudgetIsAuto() {
    assertThat(
            policy
                .decide(
                    autonomy(false, false, false, false, "0"),
                    MiConstants.TYPE_SET_BUDGET,
                    new BigDecimal("5000000"),
                    false)
                .auto())
        .isTrue();
  }

  @Test
  @DisplayName("A permitted bill payment within the limit is automatic")
  void billsWithinLimitAreAuto() {
    var decision =
        policy.decide(
            allPermissionsOn(), MiConstants.TYPE_PAY_BILLS, new BigDecimal("2000000"), false);

    assertThat(decision.auto()).isTrue();
    assertThat(decision.reason()).isEqualTo(MiConstants.REASON_OK);
  }

  @Test
  @DisplayName("The same bill payment above the limit needs confirmation")
  void billsOverLimitNeedConfirmation() {
    var decision =
        policy.decide(
            autonomy(true, true, true, false, "2000000"),
            MiConstants.TYPE_PAY_BILLS,
            new BigDecimal("2000001"),
            false);

    assertThat(decision.auto()).isFalse();
    assertThat(decision.reason()).isEqualTo(MiConstants.REASON_OVER_LIMIT);
  }

  @Test
  @DisplayName("A transfer to an unsaved recipient is never automatic")
  void transferToUnsavedRecipientNeedsConfirmation() {
    var decision =
        policy.decide(
            allPermissionsOn(), MiConstants.TYPE_TRANSFER, new BigDecimal("100000"), false);

    assertThat(decision.auto()).isFalse();
    assertThat(decision.reason()).isEqualTo(MiConstants.REASON_PERMISSION_OFF);
  }

  @Test
  @DisplayName("An unknown proposal type falls closed rather than open")
  void unknownTypeNeedsConfirmation() {
    assertThat(
            policy
                .decide(allPermissionsOn(), "SOME_FUTURE_TYPE", new BigDecimal("1"), true)
                .auto())
        .isFalse();
  }
}
