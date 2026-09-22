package com.app.controller;

import java.util.Map;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GreenNode AgentBase requires an unauthenticated {@code GET /health} on port 8080
 * (guideline 01 §10, 03 §1).
 */
@RestController
public class HealthController {

  private final HealthEndpoint healthEndpoint;

  public HealthController(HealthEndpoint healthEndpoint) {
    this.healthEndpoint = healthEndpoint;
  }

  @GetMapping("/health")
  public ResponseEntity<Map<String, String>> health() {
    Status status = healthEndpoint.health().getStatus();
    return Status.UP.equals(status)
        ? ResponseEntity.ok(Map.of("status", "UP"))
        : ResponseEntity.status(503).body(Map.of("status", "DOWN"));
  }
}
