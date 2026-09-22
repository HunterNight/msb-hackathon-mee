package com.app.service;

import com.app.dto.request.MiRequests.NewConversationRequest;
import com.app.dto.request.MiRequests.SendMessageRequest;
import com.app.dto.response.MiResponses.ConversationSummaryDto;
import com.app.dto.response.MiResponses.CurrentConversationResponse;
import com.app.dto.response.MiResponses.MaskedTranscriptResponse;
import com.app.dto.response.MiResponses.NewConversationResponse;
import com.app.dto.response.MiResponses.TurnResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.data.domain.Pageable;

/**
 * Turn orchestration: persist → screen → context → route → tools → guardrails → emit
 * (design §8.1–8.3).
 */
public interface ChatService {

  CurrentConversationResponse current(UUID customerId, int limit, Locale locale);

  NewConversationResponse start(UUID customerId, NewConversationRequest request, Locale locale);

  List<ConversationSummaryDto> conversations(UUID customerId, Pageable pageable);

  /**
   * Runs one turn. Each produced item is handed to {@code emit} as it is ready, so the SSE
   * controller streams and the JSON controller simply collects.
   *
   * @param emit (eventName, payload)
   */
  TurnResponse turn(
      UUID customerId,
      UUID conversationId,
      SendMessageRequest request,
      Locale locale,
      BiConsumer<String, Object> emit);

  void feedback(UUID customerId, UUID conversationId, UUID messageId, String rating,
      String comment);

  MaskedTranscriptResponse maskedTranscript(UUID conversationId);
}
