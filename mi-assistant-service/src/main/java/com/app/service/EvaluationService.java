package com.app.service;

import com.app.dto.request.MiRequests.EvalRunRequest;
import com.app.dto.response.MiResponses.EvalRunResponse;
import com.app.dto.response.MiResponses.McpServerDto;
import java.util.List;

/** The CMS evaluation runner: retrieval, tool choice and routing, all checked deterministically. */
public interface EvaluationService {

  EvalRunResponse run(EvalRunRequest request);

  List<McpServerDto> mcpServers();
}
