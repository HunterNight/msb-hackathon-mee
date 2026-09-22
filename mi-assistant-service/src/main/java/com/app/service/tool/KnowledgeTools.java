package com.app.service.tool;

import com.app.constant.MiConstants;
import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.RetrievedChunk;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.service.rag.RetrievalService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Mi-local tools: retrieval and the clarification path (design §3.3). */
public final class KnowledgeTools {

  private KnowledgeTools() {}

  @Component
  public static class SearchKnowledge implements MiTool {

    private final RetrievalService retrieval;

    public SearchKnowledge(RetrievalService retrieval) {
      this.retrieval = retrieval;
    }

    @Override
    public String code() {
      return ToolCodes.SEARCH_KNOWLEDGE;
    }

    @Override
    public boolean mutating() {
      return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      List<String> collections =
          args.get("collections") instanceof List<?> list
              ? list.stream().map(Object::toString).toList()
              : List.of(MiConstants.COLLECTION_GENERAL, MiConstants.COLLECTION_HOWTO);
      List<RetrievedChunk> chunks =
          retrieval.search(
              collections,
              String.valueOf(args.getOrDefault("query", "")),
              context.locale(),
              MiConstants.RAG_TOP_K);
      return new ToolResult(
          code(), true, chunks, null, (System.nanoTime() - started) / 1_000_000);
    }
  }

  @Component
  public static class AskClarification implements MiTool {

    @Override
    public String code() {
      return ToolCodes.ASK_CLARIFICATION;
    }

    @Override
    public boolean mutating() {
      return false;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      return new ToolResult(code(), true, Map.of("clarify", true), null, 0);
    }
  }
}
