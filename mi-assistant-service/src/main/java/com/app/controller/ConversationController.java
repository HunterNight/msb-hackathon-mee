package com.app.controller;

import com.app.constant.AppConstants;
import com.app.constant.MiConstants;
import com.app.constant.Routes;
import com.app.config.CurrentCustomer;
import com.app.dto.request.MiRequests.FeedbackRequest;
import com.app.dto.request.MiRequests.NewConversationRequest;
import com.app.dto.request.MiRequests.SendMessageRequest;
import com.app.dto.response.ApiResponse;
import com.app.dto.response.MiResponses.ConversationSummaryDto;
import com.app.dto.response.MiResponses.CurrentConversationResponse;
import com.app.dto.response.MiResponses.ErrorEventDto;
import com.app.dto.response.MiResponses.NewConversationResponse;
import com.app.dto.response.MiResponses.RecordedResponse;
import com.app.dto.response.MiResponses.TurnResponse;
import com.app.exception.BusinessException;
import com.app.service.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Screens 06–08. The customer's identity always comes from the JWT, never from a header. */
@RestController
@PreAuthorize("hasRole('customer')")
public class ConversationController {

  private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

  private final ChatService chatService;

  public ConversationController(ChatService chatService) {
    this.chatService = chatService;
  }

  @GetMapping(Routes.CONVERSATION_CURRENT)
  public ApiResponse<CurrentConversationResponse> current(
      @CurrentCustomer UUID customerId,
      @RequestParam(defaultValue = "50") int limit,
      Locale locale) {
    return ApiResponse.ok(chatService.current(customerId, limit, locale));
  }

  @PostMapping(Routes.CONVERSATIONS)
  public ApiResponse<NewConversationResponse> start(
      @CurrentCustomer UUID customerId,
      @RequestBody(required = false) NewConversationRequest request,
      Locale locale) {
    return ApiResponse.ok(chatService.start(customerId, request, locale));
  }

  @GetMapping(Routes.CONVERSATIONS)
  public ApiResponse<List<ConversationSummaryDto>> conversations(
      @CurrentCustomer UUID customerId,
      @PageableDefault(size = AppConstants.PAGE_SIZE_DEFAULT) Pageable pageable) {
    return ApiResponse.ok(chatService.conversations(customerId, pageable));
  }

  /**
   * The streaming variant. The turn runs on the request thread and pushes each block as it is
   * ready; a failure is delivered as an {@code error} event followed by {@code done}, so the chat
   * stays coherent instead of the stream simply dying.
   */
  @PostMapping(value = Routes.CONVERSATION_MESSAGES, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @CurrentCustomer UUID customerId,
      @PathVariable UUID id,
      @Valid @RequestBody SendMessageRequest request,
      Locale locale) {

    SseEmitter emitter = new SseEmitter(MiConstants.SSE_TIMEOUT.toMillis());
    try {
      chatService.turn(
          customerId,
          id,
          request,
          locale,
          (event, payload) -> send(emitter, event, payload));
      emitter.complete();
    } catch (BusinessException e) {
      send(emitter, MiConstants.SSE_ERROR, new ErrorEventDto(e.getErrorCode().code,
          e.getErrorCode().messageKey));
      send(emitter, MiConstants.SSE_DONE, java.util.Map.of("conversationId", id));
      emitter.complete();
    }
    return emitter;
  }

  @PostMapping(
      value = Routes.CONVERSATION_MESSAGES,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<TurnResponse> turn(
      @CurrentCustomer UUID customerId,
      @PathVariable UUID id,
      @Valid @RequestBody SendMessageRequest request,
      Locale locale) {
    return ApiResponse.ok(chatService.turn(customerId, id, request, locale, (event, payload) -> {}));
  }

  @PostMapping(Routes.CONVERSATION_FEEDBACK)
  public ApiResponse<RecordedResponse> feedback(
      @CurrentCustomer UUID customerId,
      @PathVariable UUID id,
      @Valid @RequestBody FeedbackRequest request) {
    chatService.feedback(customerId, id, request.messageId(), request.rating(), request.comment());
    return ApiResponse.ok(new RecordedResponse(true));
  }

  private void send(SseEmitter emitter, String event, Object payload) {
    try {
      emitter.send(SseEmitter.event().name(event).data(payload));
    } catch (Exception e) {
      // The client hung up mid-turn; the messages are already persisted, so this is not an error.
      log.debug("sse client disconnected during {}", event);
    }
  }
}
