package com.app.service;

import com.app.model.CustomerAutonomy;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The truth table of design §3.2. Nothing else decides whether Mi may act without asking, and the
 * default answer is always "ask".
 */
public interface AutonomyPolicy {

  Decision decide(CustomerAutonomy autonomy, String type, BigDecimal amount,
      boolean beneficiarySaved);

  CustomerAutonomy settings(UUID customerId);

  record Decision(String decision, String reason) {

    public boolean auto() {
      return com.app.constant.MiConstants.DECISION_AUTO.equals(decision);
    }
  }
}
