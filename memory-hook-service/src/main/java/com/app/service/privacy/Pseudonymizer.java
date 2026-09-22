package com.app.service.privacy;

import com.app.service.crypto.KeyService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place that turns a customer id into a namespace. Everything persisted, logged or keyed
 * on Kafka uses the result; the real id lives only inside the request scope (design §4).
 */
@Component
public class Pseudonymizer {

  private final KeyService keyService;

  public Pseudonymizer(KeyService keyService) {
    this.keyService = keyService;
  }

  public String of(UUID customerId) {
    return keyService.pseudoId(customerId);
  }

  /** Short, non-reversible form for log lines and ops screens. */
  public String shortForm(String pseudoId) {
    return pseudoId == null || pseudoId.length() < 12 ? pseudoId : pseudoId.substring(0, 12);
  }
}
