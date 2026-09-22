package com.app.service.impl;

import com.app.constant.MiConstants;
import com.app.model.Budget;
import com.app.repository.BudgetRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** SET_BUDGET is the one proposal Mi owns end to end: no peer, no money, no step-up. */
@Component
public class BudgetWriter {

  private final BudgetRepository budgets;

  public BudgetWriter(BudgetRepository budgets) {
    this.budgets = budgets;
  }

  @Transactional
  public void apply(UUID customerId, Map<String, Object> params) {
    String category = String.valueOf(params.getOrDefault("categoryCode", "OTHER"));
    BigDecimal limit = new BigDecimal(String.valueOf(params.getOrDefault("monthlyLimit", "0")));

    Budget budget =
        budgets
            .findByCustomerIdAndCategoryCodeAndActiveTrue(customerId, category)
            .orElseGet(
                () -> {
                  Budget fresh = new Budget();
                  fresh.setCustomerId(customerId);
                  fresh.setCategoryCode(category);
                  return fresh;
                });
    budget.setMonthlyLimit(limit);
    budget.setAlertPct(
        params.get("alertPct") == null
            ? MiConstants.BUDGET_ALERT_PCT_DEFAULT
            : Integer.parseInt(params.get("alertPct").toString()));
    budget.setActive(true);
    budgets.save(budget);
  }
}
