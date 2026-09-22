package com.app.service.agent;

import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.ModelProfile;
import com.app.dto.internal.MiInternal.RouterConfig;
import com.app.dto.internal.MiInternal.ToolSpec;
import com.app.dto.internal.MiInternal.TriggerRule;
import java.util.List;
import java.util.Optional;

/**
 * The CMS definitions, cached for {@code AGENT_CACHE_TTL} and invalidated by the CMS webhook.
 * Adding an agent is a CMS edit, not a deploy (design §M3.5).
 */
public interface AgentRegistry {

  AgentSpec agent(String code);

  List<AgentSpec> enabledAgents();

  RouterConfig router();

  List<ToolSpec> tools();

  Optional<ToolSpec> tool(String code);

  List<TriggerRule> triggers();

  Optional<ModelProfile> profile(String taskClass);

  /** Every collection any enabled agent may read, for the widened retrieval fallback. */
  List<String> allCollectionCodes();

  void invalidate(String agentCode);

  void invalidateAll();
}
