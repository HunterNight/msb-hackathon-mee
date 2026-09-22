package com.app.service.impl;

import com.app.constant.AgentCodes;
import com.app.constant.MiConstants;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.ModelReply;
import com.app.dto.internal.MiInternal.RetrievedChunk;
import com.app.dto.internal.MiInternal.RouteDecision;
import com.app.dto.internal.MiInternal.ToolSpec;
import com.app.dto.request.MiRequests.EvalCase;
import com.app.dto.request.MiRequests.EvalRunRequest;
import com.app.dto.response.MiResponses.EvalCaseResult;
import com.app.dto.response.MiResponses.EvalRunResponse;
import com.app.dto.response.MiResponses.McpServerDto;
import com.app.service.EvaluationService;
import com.app.service.agent.AgentRegistry;
import com.app.service.model.ModelGateway;
import com.app.service.rag.RetrievalService;
import com.app.service.tool.ToolRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EvaluationServiceImpl implements EvaluationService {

  private final RetrievalService retrieval;
  private final ModelGateway modelGateway;
  private final AgentRegistry registry;
  private final ToolRegistry tools;
  private final String cmsUrl;

  public EvaluationServiceImpl(
      RetrievalService retrieval,
      ModelGateway modelGateway,
      AgentRegistry registry,
      ToolRegistry tools,
      @Value("${app.clients.cms}") String cmsUrl) {
    this.retrieval = retrieval;
    this.modelGateway = modelGateway;
    this.registry = registry;
    this.tools = tools;
    this.cmsUrl = cmsUrl;
  }

  @Override
  public EvalRunResponse run(EvalRunRequest request) {
    List<EvalCaseResult> details = new ArrayList<>();
    int passed = 0;

    for (int index = 0; index < request.cases().size(); index++) {
      EvalCase testCase = request.cases().get(index);
      Map<String, Object> actual = new HashMap<>();
      boolean ok = true;

      if (testCase.expectedDocIds() != null && !testCase.expectedDocIds().isEmpty()) {
        List<RetrievedChunk> chunks =
            retrieval.search(
                List.of(MiConstants.COLLECTION_GENERAL, MiConstants.COLLECTION_HOWTO),
                testCase.input(),
                Locale.forLanguageTag("vi"),
                MiConstants.RAG_TOP_K);
        List<UUID> retrieved = chunks.stream().map(RetrievedChunk::docId).distinct().toList();
        actual.put("docIds", retrieved);
        // A retrieval case passes when any expected document made the top-k.
        ok = testCase.expectedDocIds().stream().anyMatch(retrieved::contains);
      }

      if (ok && testCase.expectedDomain() != null) {
        RouteDecision decision =
            modelGateway.classify(testCase.input(), List.of(), Locale.forLanguageTag("vi"));
        actual.put("domain", decision.domain());
        actual.put("confidence", decision.confidence());
        ok = testCase.expectedDomain().equals(decision.domain());
      }

      if (ok && testCase.expectedTool() != null) {
        AgentSpec agent = registry.agent(AgentCodes.GENERAL);
        List<ToolSpec> available = tools.available(agent, false);
        ModelReply reply =
            modelGateway.chat(
                agent, "", List.of(), testCase.input(), available,
                Locale.forLanguageTag("vi"));
        actual.put("tool", reply.toolCode());
        actual.put("args", reply.toolArgs());
        ok = testCase.expectedTool().equals(reply.toolCode());
        if (ok && testCase.expectedArgs() != null) {
          ok =
              testCase.expectedArgs().entrySet().stream()
                  .allMatch(
                      entry ->
                          reply.toolArgs() != null
                              && String.valueOf(entry.getValue())
                                  .equals(String.valueOf(reply.toolArgs().get(entry.getKey()))));
        }
      }

      details.add(new EvalCaseResult(index, ok, actual));
      passed += ok ? 1 : 0;
    }
    return new EvalRunResponse(UUID.randomUUID(), request.cases().size(), passed, details);
  }

  @Override
  public List<McpServerDto> mcpServers() {
    // The Tool Gateway's view for the CMS. Mi's own tools are in-process, so the only remote
    // catalogue it reports is the CMS one until MCP servers are registered.
    return List.of(
        new McpServerDto("cms", cmsUrl, registry.tools().size(), Instant.now(), false));
  }
}
