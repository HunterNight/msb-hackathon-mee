package com.app.service.ui;

import com.app.dto.internal.MiInternal.RetrievedChunk;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.dto.response.MiResponses.MessageDto;
import com.app.model.Conversation;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Turns a tool result into the versioned UI blocks the app renders — card, chart, steps — with a
 * text fallback for a client that does not know the block yet (design §M3.11).
 */
public interface BlockAssembler {

  /**
   * @param grounded true when the reply text actually carries a citation into {@code chunks} —
   *     false for a free-form model answer that merely had those chunks as RAG context. Only a
   *     grounded reply may attach a how-to chunk's steps card; otherwise the guide can belong to
   *     a completely different question than the one just answered (found live: "Tôi là ai"
   *     answered from the model, with zero citations, still attached a "how to transfer" guide).
   */
  List<MessageDto> assemble(
      Conversation conversation,
      UUID customerId,
      ToolResult toolResult,
      List<RetrievedChunk> chunks,
      boolean grounded,
      Locale locale);
}
