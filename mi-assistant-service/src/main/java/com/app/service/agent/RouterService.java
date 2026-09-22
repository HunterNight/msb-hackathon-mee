package com.app.service.agent;

import com.app.dto.internal.MiInternal.RouteDecision;
import java.util.List;
import java.util.Locale;

/**
 * Level 3 routing. Below the configured confidence the conversation stays where it is, which is
 * what keeps a vague turn from bouncing between agents (design §3.3).
 */
public interface RouterService {

  Route route(String currentAgentCode, String text, List<String> history, Locale locale);

  record Route(String agentCode, RouteDecision decision, boolean handedOff) {}
}
