package com.app.service.impl;

import com.app.constant.AgentCodes;
import com.app.constant.MiConstants;
import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.Guardrails;
import com.app.dto.internal.MiInternal.LocalizedText;
import com.app.dto.internal.MiInternal.ModelProfile;
import com.app.dto.internal.MiInternal.RouterConfig;
import com.app.dto.internal.MiInternal.RouterDomain;
import com.app.dto.internal.MiInternal.ToolSpec;
import com.app.dto.internal.MiInternal.TriggerRule;
import com.app.service.agent.AgentRegistry;
import com.app.service.client.CmsClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * A tiny TTL cache in front of the CMS. When the CMS is unreachable the last good value is served,
 * and if there never was one a built-in default keeps Mi answering (design §M3.5).
 */
@Service
public class AgentRegistryImpl implements AgentRegistry {

  private record Entry<T>(T value, Instant loadedAt) {

    boolean fresh() {
      return loadedAt.plus(MiConstants.AGENT_CACHE_TTL).isAfter(Instant.now());
    }
  }

  private final CmsClient cms;
  private final ConcurrentHashMap<String, Entry<AgentSpec>> agents = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Entry<List<?>>> lists = new ConcurrentHashMap<>();
  private volatile Entry<RouterConfig> routerEntry;

  public AgentRegistryImpl(CmsClient cms) {
    this.cms = cms;
  }

  @Override
  public AgentSpec agent(String code) {
    Entry<AgentSpec> cached = agents.get(code);
    if (cached != null && cached.fresh()) {
      return cached.value();
    }
    AgentSpec spec =
        cms.agent(code)
            .orElseGet(() -> cached != null ? cached.value() : fallbackAgent(code));
    agents.put(code, new Entry<>(spec, Instant.now()));
    return spec;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<AgentSpec> enabledAgents() {
    List<AgentSpec> fromCms = (List<AgentSpec>) cached("agents", () -> cms.agents());
    if (fromCms.isEmpty()) {
      return AgentCodes.DOMAINS.stream()
          .map(AgentCodes::defaultAgentFor)
          .distinct()
          .map(this::fallbackAgent)
          .toList();
    }
    return fromCms.stream().filter(AgentSpec::enabled).toList();
  }

  @Override
  public RouterConfig router() {
    if (routerEntry != null && routerEntry.fresh()) {
      return routerEntry.value();
    }
    RouterConfig config =
        cms.router()
            .orElseGet(() -> routerEntry != null ? routerEntry.value() : fallbackRouter());
    routerEntry = new Entry<>(config, Instant.now());
    return config;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ToolSpec> tools() {
    return (List<ToolSpec>) cached("tools", () -> cms.tools());
  }

  @Override
  public Optional<ToolSpec> tool(String code) {
    return tools().stream().filter(spec -> spec.code().equals(code)).findFirst();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<TriggerRule> triggers() {
    return (List<TriggerRule>) cached("triggers", () -> cms.triggers());
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<ModelProfile> profile(String taskClass) {
    List<ModelProfile> profiles = (List<ModelProfile>) cached("profiles", () -> cms.modelProfiles());
    return profiles.stream().filter(profile -> taskClass.equals(profile.taskClass())).findFirst();
  }

  @Override
  public List<String> allCollectionCodes() {
    return enabledAgents().stream()
        .map(AgentSpec::collectionCodes)
        .filter(java.util.Objects::nonNull)
        .flatMap(List::stream)
        .distinct()
        .toList();
  }

  @Override
  public void invalidate(String agentCode) {
    agents.remove(agentCode);
    lists.remove("agents");
  }

  @Override
  public void invalidateAll() {
    agents.clear();
    lists.clear();
    routerEntry = null;
  }

  private List<?> cached(String key, java.util.function.Supplier<List<?>> loader) {
    Entry<List<?>> entry = lists.get(key);
    if (entry != null && entry.fresh()) {
      return entry.value();
    }
    List<?> loaded = loader.get();
    if (loaded.isEmpty() && entry != null) {
      // Prefer stale-but-real over empty: an empty tool list would silently disarm every agent.
      return entry.value();
    }
    lists.put(key, new Entry<>(loaded, Instant.now()));
    return loaded;
  }

  /**
   * The shape Mi falls back to before the CMS has ever answered. It is deliberately minimal: a
   * persona, the default tool set of the code, and no elevated proposal ceiling.
   */
  private AgentSpec fallbackAgent(String code) {
    return new AgentSpec(
        code,
        new LocalizedText("MEE", "MEE"),
        domainOf(code),
        true,
        1,
        new LocalizedText(
            "Bạn là MEE, trợ lý ngân hàng của MSB. Trả lời ngắn gọn, thân thiện, bằng tiếng Việt.",
            "You are MEE, MSB's banking assistant. Answer briefly and warmly in English."),
        null,
        BigDecimal.valueOf(MiConstants.LLM_TEMPERATURE_DEFAULT),
        ToolCodes.DEFAULT_BY_AGENT.getOrDefault(code, List.of(ToolCodes.SEARCH_KNOWLEDGE)),
        List.of(MiConstants.COLLECTION_GENERAL, MiConstants.COLLECTION_HOWTO),
        new Guardrails(List.of(), "mi.reply.guardrail.offTopic", null, true,
            MiConstants.SUGGESTION_COUNT,
            // Fail closed: if the CMS is unreachable we do not know what this agent is permitted
            // to do, and opening a credit product is not a safe default guess.
            false),
        List.of(),
        null,
        "fallback");
  }

  private RouterConfig fallbackRouter() {
    List<RouterDomain> domains =
        AgentCodes.DOMAINS.stream()
            .map(
                domain ->
                    new RouterDomain(
                        AgentCodes.defaultAgentFor(domain), new LocalizedText(domain, domain)))
            .toList();
    return new RouterConfig(
        BigDecimal.valueOf(MiConstants.ROUTER_CONFIDENCE_MIN), domains, List.of(), 0);
  }

  private String domainOf(String code) {
    Map<String, String> byCode =
        Map.of(
            AgentCodes.LOAN, AgentCodes.DOMAIN_LOAN,
            AgentCodes.CARD, AgentCodes.DOMAIN_CARD,
            AgentCodes.SAVING, AgentCodes.DOMAIN_SAVING,
            AgentCodes.PAYMENT, AgentCodes.DOMAIN_PAYMENT,
            AgentCodes.ROUTER, AgentCodes.DOMAIN_ROUTER);
    return byCode.getOrDefault(code, AgentCodes.DOMAIN_GENERAL);
  }
}
