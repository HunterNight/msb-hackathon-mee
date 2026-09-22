package com.app.service.impl;

import com.app.constant.AgentCodes;
import com.app.constant.MiConstants;
import com.app.dto.internal.MiInternal.RouteDecision;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.RouterConfig;
import com.app.service.agent.AgentRegistry;
import com.app.service.agent.RouterService;
import com.app.service.model.ModelGateway;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RouterServiceImpl implements RouterService {

  private final ModelGateway modelGateway;
  private final AgentRegistry registry;
  private final boolean multiAgentEnabled;

  public RouterServiceImpl(
      ModelGateway modelGateway,
      AgentRegistry registry,
      @Value("${app.mi.multiagent.enabled}") boolean multiAgentEnabled) {
    this.modelGateway = modelGateway;
    this.registry = registry;
    this.multiAgentEnabled = multiAgentEnabled;
  }

  @Override
  public Route route(String currentAgentCode, String text, List<String> history, Locale locale) {
    String current = currentAgentCode == null ? AgentCodes.GENERAL : currentAgentCode;
    if (!multiAgentEnabled) {
      // Level 1 and 2 ship with one persona and one agent.
      return new Route(
          AgentCodes.GENERAL,
          new RouteDecision(AgentCodes.DOMAIN_GENERAL, 1.0, null),
          false);
    }

    RouteDecision decision =
        modelGateway.classify(
            text, history.stream().map(turn -> new ModelGateway.Turn("history", turn)).toList(),
            locale);

    RouterConfig config = registry.router();
    BigDecimal minimum =
        config.confidenceMin() == null
            ? BigDecimal.valueOf(MiConstants.ROUTER_CONFIDENCE_MIN)
            : config.confidenceMin();

    if (decision.confidence() < minimum.doubleValue()) {
      return new Route(current, decision, false);
    }

    // The CMS router config names the agents, and each agent declares the domain it owns, so the
    // mapping is read from the agent list rather than duplicated in the router row.
    String target =
        registry.enabledAgents().stream()
            .filter(agent -> decision.domain().equals(agent.domain()))
            .map(AgentSpec::code)
            .findFirst()
            .orElseGet(() -> AgentCodes.defaultAgentFor(decision.domain()));

    // A disabled agent falls back to general rather than failing the turn (MI-010).
    if (!registry.agent(target).enabled()) {
      return new Route(AgentCodes.GENERAL, decision, !AgentCodes.GENERAL.equals(current));
    }
    return new Route(target, decision, !target.equals(current));
  }
}
