package com.app.service.tool;

import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.dto.internal.MiInternal.ToolSpec;
import java.util.List;
import java.util.Map;

/** Resolves a tool code to a bean, after checking that this agent is allowed to call it. */
public interface ToolRegistry {

  /** The tools the agent may use this turn; empty when the turn is guarded (design §12.2). */
  List<ToolSpec> available(AgentSpec agent, boolean toolsDisabled);

  ToolResult invoke(
      AgentSpec agent, String code, MiTool.Context context, Map<String, Object> args);
}
