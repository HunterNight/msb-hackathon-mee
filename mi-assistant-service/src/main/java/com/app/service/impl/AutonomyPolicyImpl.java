package com.app.service.impl;

import com.app.constant.MiConstants;
import com.app.model.CustomerAutonomy;
import com.app.repository.CustomerAutonomyRepository;
import com.app.service.AutonomyPolicy;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutonomyPolicyImpl implements AutonomyPolicy {

  private final CustomerAutonomyRepository autonomies;

  public AutonomyPolicyImpl(CustomerAutonomyRepository autonomies) {
    this.autonomies = autonomies;
  }

  @Override
  public Decision decide(
      CustomerAutonomy autonomy, String type, BigDecimal amount, boolean beneficiarySaved) {

    if (autonomy.isPaused()) {
      return new Decision(MiConstants.DECISION_CONFIRM, MiConstants.REASON_PAUSED);
    }
    // A budget moves no money, so it is the one thing Mi may always do.
    if (MiConstants.TYPE_SET_BUDGET.equals(type)) {
      return new Decision(MiConstants.DECISION_AUTO, MiConstants.REASON_OK);
    }
    // Opening a card is not a payment, so the per-transaction auto limit says nothing about it: it
    // creates a contractual obligation, a bureau footprint and recurring fees. No permission in
    // CUSTOMER_AUTONOMY may stand in for that consent, so this is unconditional and deliberately
    // sits above the permission switch — adding CARD_ISSUE there later still could not make it
    // automatic (design 07 §3.2).
    if (MiConstants.TYPE_CARD_ISSUE.equals(type)) {
      return new Decision(MiConstants.DECISION_CONFIRM, MiConstants.REASON_PRODUCT_CONSENT);
    }

    boolean permitted =
        switch (type) {
          case MiConstants.TYPE_PAY_BILLS -> autonomy.isPermRecurringBills();
          case MiConstants.TYPE_TRANSFER -> autonomy.isPermSavedRecipients() && beneficiarySaved;
          case MiConstants.TYPE_GOAL_TOPUP -> autonomy.isPermAutoSaving();
          default -> false;
        };
    if (!permitted) {
      return new Decision(MiConstants.DECISION_CONFIRM, MiConstants.REASON_PERMISSION_OFF);
    }
    BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
    if (value.compareTo(autonomy.getAutoLimit()) > 0) {
      return new Decision(MiConstants.DECISION_CONFIRM, MiConstants.REASON_OVER_LIMIT);
    }
    return new Decision(MiConstants.DECISION_AUTO, MiConstants.REASON_OK);
  }

  @Override
  @Transactional
  public CustomerAutonomy settings(UUID customerId) {
    return autonomies
        .findById(customerId)
        .orElseGet(
            () -> {
              CustomerAutonomy fresh = new CustomerAutonomy();
              fresh.setCustomerId(customerId);
              return autonomies.save(fresh);
            });
  }
}
