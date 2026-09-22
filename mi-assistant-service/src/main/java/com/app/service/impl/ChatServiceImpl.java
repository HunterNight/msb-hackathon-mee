package com.app.service.impl;

import com.app.constant.AgentCodes;
import com.app.constant.ErrorCode;
import com.app.constant.MiConstants;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.ModelReply;
import com.app.dto.internal.MiInternal.QuickPrompt;
import com.app.dto.internal.MiInternal.RetrievedChunk;
import com.app.dto.internal.MiInternal.ScreeningResult;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.dto.internal.MiInternal.ToolSpec;
import com.app.dto.request.MiRequests.NewConversationRequest;
import com.app.dto.request.MiRequests.SendMessageRequest;
import com.app.dto.request.MiRequests.TurnContext;
import com.app.dto.response.MiResponses.ChipDto;
import com.app.dto.response.MiResponses.ConversationSummaryDto;
import com.app.dto.response.MiResponses.CurrentConversationResponse;
import com.app.dto.response.MiResponses.MaskedToolCall;
import com.app.dto.response.MiResponses.MaskedTranscriptResponse;
import com.app.dto.response.MiResponses.MaskedTurnDto;
import com.app.dto.response.MiResponses.MessageDto;
import com.app.dto.response.MiResponses.NewConversationResponse;
import com.app.dto.response.MiResponses.ResolvedAliasDto;
import com.app.dto.response.MiResponses.SuggestionDto;
import com.app.dto.response.MiResponses.TurnResponse;
import com.app.exception.BusinessException;
import com.app.model.Conversation;
import com.app.model.Message;
import com.app.repository.ConversationRepository;
import com.app.repository.MessageRepository;
import com.app.service.ChatService;
import com.app.service.agent.AgentRegistry;
import com.app.service.agent.GuardrailService;
import com.app.service.agent.RouterService;
import com.app.service.client.CmsClient;
import com.app.service.memory.MemoryService;
import com.app.service.memory.WorkingMemory;
import com.app.service.model.ModelGateway;
import com.app.service.model.VrmComposer;
import com.app.service.rag.CitationPostProcessor;
import com.app.service.rag.RetrievalService;
import com.app.service.security.InputScreeningService;
import com.app.service.security.PiiRedactor;
import com.app.service.security.UntrustedWrapper;
import com.app.service.tool.MiTool;
import com.app.service.tool.ToolRegistry;
import com.app.service.ui.BlockAssembler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChatServiceImpl implements ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

  private final ConversationRepository conversations;
  private final MessageRepository messages;
  private final AgentRegistry registry;
  private final RouterService router;
  private final ModelGateway modelGateway;
  private final ToolRegistry tools;
  private final GuardrailService guardrails;
  private final InputScreeningService screening;
  private final PiiRedactor redactor;
  private final CitationPostProcessor citations;
  private final RetrievalService retrieval;
  private final WorkingMemory workingMemory;
  private final MemoryService memoryService;
  private final BlockAssembler blocks;
  private final CmsClient cms;
  private final MessageSource messageSource;
  private final ToolNarrator narrator;
  private final VrmComposer vrm;
  private final UntrustedWrapper untrustedWrapper;
  private final ObjectMapper objectMapper;
  private final boolean chatPayEnabled;
  private final boolean advanceEnabled;

  public ChatServiceImpl(
      ConversationRepository conversations,
      MessageRepository messages,
      AgentRegistry registry,
      RouterService router,
      ModelGateway modelGateway,
      ToolRegistry tools,
      GuardrailService guardrails,
      InputScreeningService screening,
      PiiRedactor redactor,
      CitationPostProcessor citations,
      RetrievalService retrieval,
      WorkingMemory workingMemory,
      MemoryService memoryService,
      BlockAssembler blocks,
      CmsClient cms,
      MessageSource messageSource,
      ToolNarrator narrator,
      VrmComposer vrm,
      UntrustedWrapper untrustedWrapper,
      ObjectMapper objectMapper,
      @Value("${app.mi.chatpay.enabled}") boolean chatPayEnabled,
      @Value("${app.mi.advance.enabled:false}") boolean advanceEnabled) {
    this.conversations = conversations;
    this.messages = messages;
    this.registry = registry;
    this.router = router;
    this.modelGateway = modelGateway;
    this.tools = tools;
    this.guardrails = guardrails;
    this.screening = screening;
    this.redactor = redactor;
    this.citations = citations;
    this.retrieval = retrieval;
    this.workingMemory = workingMemory;
    this.memoryService = memoryService;
    this.blocks = blocks;
    this.cms = cms;
    this.messageSource = messageSource;
    this.narrator = narrator;
    this.vrm = vrm;
    this.untrustedWrapper = untrustedWrapper;
    this.objectMapper = objectMapper;
    this.chatPayEnabled = chatPayEnabled;
    this.advanceEnabled = advanceEnabled;
  }

  @Override
  @Transactional
  public CurrentConversationResponse current(UUID customerId, int limit, Locale locale) {
    Conversation conversation =
        conversations
            .findFirstByCustomerIdAndStatusOrderByLastMessageAtDesc(customerId, "OPEN")
            .orElseGet(() -> createConversation(customerId, null));

    List<MessageDto> history =
        messages.findByConversationIdOrderBySeqAsc(conversation.getId()).stream()
            .limit(limit)
            .map(this::toDto)
            .toList();

    return new CurrentConversationResponse(
        conversation.getId(),
        history,
        history.isEmpty() ? suggestions(conversation.getActiveAgentCode(), locale) : List.of(),
        false,
        chatPayEnabled);
  }

  @Override
  @Transactional
  public NewConversationResponse start(
      UUID customerId, NewConversationRequest request, Locale locale) {
    Conversation conversation =
        createConversation(customerId, request == null ? null : request.title());
    return new NewConversationResponse(
        conversation.getId(), suggestions(conversation.getActiveAgentCode(), locale));
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConversationSummaryDto> conversations(UUID customerId, Pageable pageable) {
    return conversations.findByCustomerIdOrderByLastMessageAtDesc(customerId, pageable).stream()
        .map(
            conversation ->
                new ConversationSummaryDto(
                    conversation.getId(),
                    conversation.getTitle(),
                    conversation.getLastMessageAt(),
                    conversation.getStatus()))
        .toList();
  }

  @Override
  @Transactional
  public TurnResponse turn(
      UUID customerId,
      UUID conversationId,
      SendMessageRequest request,
      Locale locale,
      BiConsumer<String, Object> emit) {

    Conversation conversation =
        conversations
            .findByIdAndCustomerId(conversationId, customerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

    // 1 · screen before anything is stored, so a secret never reaches the database.
    ScreeningResult screened = screening.screen(customerId, request.text());
    // The turn trace is deliberately about decisions, not content: the customer's words are
    // already stored masked, and repeating them in a log line would undo that.
    log.debug(
        "turn conversation={} chars={} label={} injectionSuspected={} refused={}",
        conversationId,
        request.text() == null ? 0 : request.text().length(),
        screened.label(),
        screened.injectionSuspected(),
        screened.refused());

    Message userMessage = persistUser(conversation, customerId, screened.sanitisedText());
    MessageDto userDto = toDto(userMessage, request.clientMessageId());
    emit.accept(MiConstants.SSE_MESSAGE_USER, userDto);
    emit.accept(MiConstants.SSE_TYPING, Map.of());
    workingMemory.remember(conversationId, MiConstants.ROLE_USER, screened.sanitisedText());

    List<MessageDto> produced = new ArrayList<>();
    produced.add(userDto);

    // 2 · an illegal or self-harm turn is answered with a canned refusal and nothing else.
    if (screened.refused()) {
      boolean review = screening.flag(customerId, screened.label(), request.text());
      if (review) {
        log.warn("customer flagged for fraud review after repeated refused turns");
      }
      MessageDto refusal =
          emitText(
              conversation,
              customerId,
              messageSource.getMessage(screened.refusalReasonKey(), null,
                  screened.refusalReasonKey(), locale),
              List.of(),
              List.of(),
              conversation.getActiveAgentCode(),
              MiConstants.SAFETY_REFUSED,
              emit);
      produced.add(refusal);
      emit.accept(
          MiConstants.SSE_DONE,
          new com.app.dto.response.MiResponses.DoneDto(
              conversationId, conversation.getActiveAgentCode(), userMessage.getId()));
      return new TurnResponse(produced, conversation.getActiveAgentCode());
    }

    // 3 · route, then build the agent from the CMS definition.
    List<String> history = workingMemory.recent(conversationId, MiConstants.CHAT_HISTORY_WINDOW);
    RouterService.Route route =
        router.route(conversation.getActiveAgentCode(), screened.sanitisedText(), history, locale);
    if (route.handedOff()) {
      conversation.setActiveAgentCode(route.agentCode());
      conversations.save(conversation);
    }
    AgentSpec agent = registry.agent(route.agentCode());
    log.debug(
        "route domain={} confidence={} agent={} handedOff={} collections={}",
        route.decision().domain(),
        String.format("%.2f", route.decision().confidence()),
        agent.code(),
        route.handedOff(),
        agent.collectionCodes());

    // 4 · retrieval and memory become untrusted context; tools are dropped on a guarded turn.
    // Retrieval is one input to a reply, not a precondition for having one. The embedding call shares the
    // hosted model's rate limit, and when it failed (429) the whole turn was answered "hệ thống đang bận" —
    // including "xin chào" and "cảm ơn", which need no documents at all. Without them the turn goes on:
    // small talk, tools and rules still answer, and a knowledge question gets the honest "no information".
    List<RetrievedChunk> chunks;
    try {
      chunks = retrieve(agent, screened.sanitisedText(), locale);
    } catch (RuntimeException e) {
      log.warn("retrieval failed, continuing without documents: {}", e.toString());
      chunks = List.of();
    }
    String memoryIntent =
        MiConstants.memoryIntent(route.decision().domain(), screened.sanitisedText());
    List<String> recalled =
        memoryService.recallForPrompt(customerId, screened.sanitisedText(), memoryIntent);
    List<ToolSpec> available = tools.available(agent, screened.injectionSuspected());
    // Where the customer is, so "this loan" resolves (M3.12). A turn sent from the chat screen
    // carries none, so the last screen the customer came from is remembered for the conversation
    // and reused — otherwise the context would survive exactly one message (06 §6).
    MiTool.ScreenContext screenContext = resolveScreenContext(conversationId, request.context());
    log.debug(
        "context chunks={} topScore={} memoryRecords={} toolsAvailable={} screen={} entity={}",
        chunks.size(),
        chunks.isEmpty() ? "-" : String.format("%.3f", chunks.get(0).score()),
        recalled.size(),
        available.stream().map(ToolSpec::code).toList(),
        screenContext == null ? "-" : screenContext.screen(),
        screenContext == null || !screenContext.hasEntity()
            ? "-"
            : screenContext.entityType());

    ModelReply reply;
    try {
      reply =
          modelGateway.chat(
              agent,
              systemPrompt(agent, locale, screenContext),
              history.stream().map(turn -> new ModelGateway.Turn("history", turn)).toList(),
              screened.sanitisedText(),
              available,
              locale);
    } catch (RuntimeException e) {
      emit.accept(
          MiConstants.SSE_ERROR,
          new com.app.dto.response.MiResponses.ErrorEventDto(
              ErrorCode.LLM_UNAVAILABLE.code,
              messageSource.getMessage(
                  ErrorCode.LLM_UNAVAILABLE.messageKey, null, locale)));
      throw new BusinessException(ErrorCode.LLM_UNAVAILABLE);
    }

    log.debug(
        "model profile={} tool={} args={} textLength={}",
        reply.modelProfile(),
        reply.toolCode(),
        reply.toolArgs() == null ? Map.of() : reply.toolArgs().keySet(),
        reply.text() == null ? 0 : reply.text().length());

    // 5 · run the tool the model asked for; the registry re-checks the allow-list.
    ToolResult toolResult = null;
    if (reply.toolCode() != null && !available.isEmpty()) {
      MiTool.Context context =
          new MiTool.Context(
              customerId, conversationId, userMessage.getId(), locale, null, screenContext);
      toolResult = tools.invoke(agent, reply.toolCode(), context, reply.toolArgs());
      log.debug(
          "tool {} ok={} error={} latencyMs={}",
          toolResult.code(),
          toolResult.ok(),
          toolResult.errorCode(),
          toolResult.latencyMs());
    }

    // 6 · assemble the answer: text, then whichever block the tool produced.
    boolean advance = isAdvance(request);
    String text =
        advance
            ? composeAdvance(
                agent, reply, toolResult, chunks, recalled, screened.sanitisedText(), locale, emit)
            : composeDelegating(
                agent,
                reply,
                toolResult,
                chunks,
                recalled,
                screened.sanitisedText(),
                customerId,
                conversationId,
                locale);
    CitationPostProcessor.Result processed = citations.process(text, chunks);
    String safeText =
        guardrails.checkReply(
            agent,
            processed.text(),
            !processed.citations().isEmpty() || toolResult != null,
            !recalled.isEmpty());
    log.debug(
        "reply citations={} safety={} length={}",
        processed.citations().stream().map(c -> c.title()).toList(),
        screened.injectionSuspected() ? MiConstants.SAFETY_GUARDED : MiConstants.SAFETY_OK,
        safeText == null ? 0 : safeText.length());

    MessageDto miMessage =
        emitText(
            conversation,
            customerId,
            safeText,
            processed.citations(),
            chips(agent, toolResult, locale),
            agent.code(),
            screened.injectionSuspected() ? MiConstants.SAFETY_GUARDED : MiConstants.SAFETY_OK,
            emit);
    produced.add(miMessage);
    workingMemory.remember(conversationId, MiConstants.ROLE_MI, safeText);

    boolean grounded = !processed.citations().isEmpty();
    for (MessageDto block :
        blocks.assemble(conversation, customerId, toolResult, chunks, grounded, locale)) {
      persistBlock(conversation, customerId, block);
      emit.accept(eventFor(block.kind()), block);
      produced.add(block);
    }

    emit.accept(
        MiConstants.SSE_DONE,
        new com.app.dto.response.MiResponses.DoneDto(
            conversationId, agent.code(), userMessage.getId()));
    return new TurnResponse(produced, agent.code());
  }

  @Override
  @Transactional
  public void feedback(
      UUID customerId, UUID conversationId, UUID messageId, String rating, String comment) {
    Message message =
        messages
            .findByIdAndCustomerId(messageId, customerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    cms.feedback(conversationId, messageId, message.getAgentCode(), rating, comment, customerId);
  }

  @Override
  @Transactional(readOnly = true)
  public MaskedTranscriptResponse maskedTranscript(UUID conversationId) {
    Conversation conversation =
        conversations
            .findById(conversationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

    List<MaskedTurnDto> turns =
        messages.findByConversationIdOrderBySeqAsc(conversationId).stream()
            .limit(MiConstants.MASKED_TRANSCRIPT_MAX_TURNS)
            .map(
                message ->
                    new MaskedTurnDto(
                        message.getRole(),
                        redactor.maskForTranscript(message.getText(), Map.of()),
                        message.getAgentCode(),
                        List.<MaskedToolCall>of(),
                        message.getCreatedAt()))
            .toList();

    return new MaskedTranscriptResponse(
        conversationId,
        conversation.getCustomerId(),
        conversation.getLastMessageAt(),
        turns,
        List.<ResolvedAliasDto>of());
  }

  // ── internals ──────────────────────────────────────────────────────────────

  private List<RetrievedChunk> retrieve(AgentSpec agent, String question, Locale locale) {
    List<String> collections =
        agent.collectionCodes() == null || agent.collectionCodes().isEmpty()
            ? List.of(MiConstants.COLLECTION_GENERAL, MiConstants.COLLECTION_HOWTO)
            : agent.collectionCodes();
    List<RetrievedChunk> scoped =
        retrieval.search(collections, question, locale, MiConstants.RAG_TOP_K);
    if (!retrieval.belowThreshold(scoped)) {
      return scoped;
    }
    // The agent's own collections had nothing convincing. A customer asking about the interest-free
    // period should not be failed because the router put them with the general agent, so widen the
    // search rather than answer from a weak match.
    List<RetrievedChunk> wide =
        retrieval.search(registry.allCollectionCodes(), question, locale, MiConstants.RAG_TOP_K);
    return retrieval.belowThreshold(wide) ? scoped : wide;
  }

  /**
   * The system section is the only place with instructions; everything else arrives wrapped as
   * untrusted data (design §12.1).
   *
   * <p>The screen context is appended as an {@code <untrusted source="screen">} block rather than as
   * plain prose: it is client-supplied, so it is data the model may read to interpret "this loan",
   * never an instruction it should follow.
   */
  private String systemPrompt(AgentSpec agent, Locale locale, MiTool.ScreenContext screen) {
    String persona =
        agent.systemPrompt() == null ? "" : agent.systemPrompt().forLocale(locale);
    String prompt =
        persona + "\n" + messageSource.getMessage("mi.prompt.rules", null, "", locale);
    if (screen == null || screen.screen() == null) {
      return prompt;
    }
    String described =
        screen.hasEntity()
            ? screen.screen() + " (" + screen.entityType() + ")"
            : screen.screen();
    return prompt
        + "\n"
        + messageSource.getMessage("mi.prompt.screen", null, "", locale)
        + " "
        + untrustedWrapper.wrap("screen", described);
  }

  /**
   * The turn's own context when the app sent one, otherwise the last one seen in this conversation.
   * Navigating into the chat screen sends the entry context on the first message only, so without
   * this a follow-up ("và nếu trả trước 30 triệu?") would lose the loan.
   */
  private MiTool.ScreenContext resolveScreenContext(
      UUID conversationId, TurnContext incoming) {

    if (incoming != null && incoming.screen() != null) {
      MiTool.ScreenContext resolved =
          new MiTool.ScreenContext(incoming.screen(), incoming.entityType(), incoming.entityId());
      workingMemory.rememberScreen(
          conversationId, resolved.screen(), resolved.entityType(), resolved.entityId());
      return resolved;
    }
    return workingMemory
        .screen(conversationId)
        .map(
            stored ->
                new MiTool.ScreenContext(stored.screen(), stored.entityType(), stored.entityId()))
        .orElse(null);
  }

  /**
   * Standard composition, with the wording of a grounded answer delegated to the VRM agent.
   *
   * <p>The flow is client → mi → VRM → mi: mi has already screened the input, routed the turn,
   * retrieved from the CMS corpus and run any tool, and it still applies guardrails, citations,
   * persistence and streaming to whatever comes back. Only the sentence the customer reads is the
   * agent's.
   *
   * <p>Delegated on exactly one shape of turn — nothing ran, and there is grounding to answer from.
   * Both halves matter:
   *
   * <ul>
   *   <li><b>A tool ran, so mi narrates.</b> {@link ToolNarrator} is deterministic Java over a real
   *       service response and takes no LLM call, which is why a balance question answers in under
   *       half a second. The agent, asked which cards a customer holds, listed two card numbers that
   *       were not theirs; it must never be the voice describing someone's money.
   *   <li><b>No grounding, so mi declines.</b> With nothing retrieved, mi says it does not have the
   *       answer, and that stays true. Handing the question over ungrounded is how the agent came to
   *       quote a six-month deposit rate of 4,7%/năm when the bank publishes 5,2%/năm.
   * </ul>
   *
   * <p>Falls back to {@link #compose} on anything at all — disabled, unreachable, timed out, empty
   * answer — so delegation can change how a reply reads but never whether one arrives.
   */
  private String composeDelegating(
      AgentSpec agent,
      ModelReply reply,
      ToolResult toolResult,
      List<RetrievedChunk> chunks,
      List<String> recalled,
      String question,
      UUID customerId,
      UUID conversationId,
      Locale locale) {

    if (vrm.enabled()
        && answerIsPureKnowledge(toolResult)
        && !chunks.isEmpty()
        && !retrieval.belowThreshold(chunks)) {

      Optional<String> delegated =
          vrm.compose(
              agent.code(),
              question,
              chunks,
              customerId,
              conversationId,
              locale);
      if (delegated.isPresent()) {
        // The citation marker mi's own answer carries is what CitationPostProcessor turns into a
        // source chip. The agent has no way to know mi's doc ids, so the best passage is tagged here.
        return delegated.get() + " [doc:" + chunks.get(0).docId() + "]";
      }
    }
    return compose(agent, reply, toolResult, chunks, recalled, locale);
  }

  /**
   * Whether the turn produced nothing but a knowledge lookup, and so is safe to hand over.
   *
   * <p>{@code search_knowledge} reads the CMS corpus and touches no customer data, and it is the tool
   * the router picks for most questions — a first version of this excluded every tool alike, which
   * silently disabled delegation on exactly the path it was built for. Every other tool either reads
   * the customer's own money or moves it, and those answers stay with {@link ToolNarrator}.
   */
  private boolean answerIsPureKnowledge(ToolResult toolResult) {
    return toolResult == null || com.app.constant.ToolCodes.SEARCH_KNOWLEDGE.equals(toolResult.code());
  }

  private String compose(
      AgentSpec agent,
      ModelReply reply,
      ToolResult toolResult,
      List<RetrievedChunk> chunks,
      List<String> recalled,
      Locale locale) {

    if (reply.text() != null && !reply.text().isBlank()) {
      return reply.text();
    }
    if (toolResult != null && toolResult.ok()) {
      return narrate(agent, toolResult, chunks, locale);
    }
    // Nothing ran at all: no text, no tool. This is the only case where Mi genuinely could not
    // interpret the turn, so it is the only case that should say so.
    if (chunks.isEmpty() || retrieval.belowThreshold(chunks)) {
      return messageSource.getMessage("mi.reply.clarify", null, locale);
    }
    return answerFrom(chunks, locale);
  }

  /** The Level 1 answer: the best passage, with its document tagged so the citation survives. */
  private String answerFrom(List<RetrievedChunk> chunks, Locale locale) {
    RetrievedChunk best = chunks.get(0);
    String body = best.content().length() > 600 ? best.content().substring(0, 600) + "…"
        : best.content();
    return body + " [doc:" + best.docId() + "]";
  }

  private String narrate(
      AgentSpec agent, ToolResult toolResult, List<RetrievedChunk> chunks, Locale locale) {
    return switch (toolResult.code()) {
      case com.app.constant.ToolCodes.PROPOSE_TRANSFER ->
          // A weak or empty resolution means no card was built — data is the {needsChoice,
          // candidates} marker (ProposalTools#transfer), not a ProposalCardDto. Saying "prepared,
          // go confirm" here would send the customer looking for a card that does not exist.
          toolResult.data() instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("needsChoice"))
              ? messageSource.getMessage(
                  map.get("candidates") instanceof List<?> list && !list.isEmpty()
                      ? "mi.reply.recipientAmbiguous"
                      : "mi.reply.recipientNotFound",
                  null,
                  locale)
              : messageSource.getMessage("mi.reply.transferReady", null, locale);
      case com.app.constant.ToolCodes.PROPOSE_BILL_PAYMENT,
              com.app.constant.ToolCodes.PROPOSE_CARD_PAYMENT,
              com.app.constant.ToolCodes.PROPOSE_DEPOSIT,
              com.app.constant.ToolCodes.PROPOSE_GOAL_TOPUP,
              com.app.constant.ToolCodes.PROPOSE_LOAN_REPAYMENT,
              com.app.constant.ToolCodes.PROPOSE_PREPAYMENT,
              com.app.constant.ToolCodes.SET_BUDGET ->
          messageSource.getMessage("mi.reply.proposalReady", null, locale);
      case com.app.constant.ToolCodes.SEARCH_KNOWLEDGE ->
          // Emptiness is not the bar: a weak match is worse than none, because it is served with
          // a citation and reads as an answer. "Thời tiết hôm nay thế nào?" scored 0.37 against
          // the interest-free-period page and came back as if it were the reply.
          knowledgeAnswer(agent, chunks, locale);      default -> {
        String narrated = narrator.narrate(toolResult, locale);
        yield narrated != null && !narrated.isBlank()
            ? narrated
            : messageSource.getMessage("mi.reply.toolDone", null, locale);
      }
    };
  }

  /**
   * The reply to a knowledge search. Package-private so the branch can be asserted directly: which
   * of the two "no answer" messages a customer gets is a behaviour worth pinning, not an
   * implementation detail.
   *
   * <p>A search that ran and found nothing is a gap in what Mi knows, not a failure to understand
   * the customer — {@link #retrieve} has already widened to every collection by this point. Telling
   * someone who asked a perfectly clear question "mình chưa hiểu rõ ý bạn" blames them for it, and
   * the generic clarify copy then offers transfers and deposits to a customer who asked the loan
   * agent about a loan.
   */
  String knowledgeAnswer(AgentSpec agent, List<RetrievedChunk> chunks, Locale locale) {
    return chunks.isEmpty() || retrieval.belowThreshold(chunks)
        ? noAnswer(agent, locale)
        : answerFrom(chunks, locale);
  }

  /**
   * What Mi says when it understood the question and simply has no answer for it.
   *
   * <p>Resolved per agent first ({@code mi.reply.noAnswer.loan}) with the generic text as the
   * fallback, so the domain that is actually handling the turn can say something on-topic and a new
   * agent gets a sensible answer with no code change — the same pattern as
   * {@code mi.card.status.<phase>}. The chips accompanying the message already come from the active
   * agent's quick prompts, so the recovery path stays inside the domain the customer is in.
   */
  private String noAnswer(AgentSpec agent, Locale locale) {
    String generic = messageSource.getMessage("mi.reply.noAnswer", null, locale);
    if (agent == null || agent.code() == null) {
      return generic;
    }
    return messageSource.getMessage("mi.reply.noAnswer." + agent.code(), null, generic, locale);
  }

  /**
   * Whether this turn asked for advance mode.
   *
   * <p>Package-private so the decision can be asserted on its own: the reported bug was that
   * {@code mode} arrived on the request and was never read, and a test that calls the composer
   * directly cannot catch that.
   */
  boolean isAdvance(SendMessageRequest request) {
    return advanceEnabled
        && request != null
        && MiConstants.MODE_ADVANCE.equals(request.modeOrDefault());
  }

  /**
   * Advance mode: report what was gathered, then let the model compose one answer from it.
   *
   * <p>Standard mode answers a turn with a single passage or a single narrated tool result, which is
   * right for "số dư của tôi?" and useless for "nhà 5 tỷ, trả được 40 triệu/tháng, vay được không và
   * có ảnh hưởng mục tiêu tiết kiệm không". That kind of question needs several sources read and then
   * reasoned about together.
   *
   * <p>Two things make this reach the model at all. The composer call passes an <em>empty</em> tool
   * list, because {@link com.app.service.impl.OpenAiModelGateway} delegates to the deterministic
   * rules gateway whenever tools are present — so a turn with tools never reaches the network. And
   * the gathered material is handed over as data, not as a question: every figure in the answer comes
   * from a tool result or a cited passage, and the model's job is to arrange and explain, never to
   * supply a number. That is the same discipline the proactive engine uses (06 §5).
   *
   * <p>Falls back to the standard composition whenever the model returns nothing, so advance mode can
   * only ever add to the answer, never replace it with silence.
   */
  String composeAdvance(
      AgentSpec agent,
      ModelReply reply,
      ToolResult toolResult,
      List<RetrievedChunk> chunks,
      List<String> recalled,
      String question,
      Locale locale,
      BiConsumer<String, Object> emit) {

    String standard = compose(agent, reply, toolResult, chunks, recalled, locale);
    StringBuilder gathered = new StringBuilder();
    int seq = 0;

    if (toolResult != null && toolResult.ok()) {
      seq++;
      emitPlanStep(emit, seq, MiConstants.STEP_KIND_TOOL, toolLabel(toolResult, locale));
      String narrated = narrator.narrate(toolResult, locale);
      if (narrated != null && !narrated.isBlank()) {
        // Tool results carry customer-supplied strings — beneficiary names, notes, goal names — and
        // this is the first place they reach a model, so they are screened and labelled first
        // (design §12.1/§12.3, audit item A2).
        gathered
            .append(untrustedWrapper.wrap("tool", toolResult.code(), screened(narrated)))
            .append('\n');
      }
    }

    List<RetrievedChunk> usable =
        retrieval.belowThreshold(chunks)
            ? List.of()
            : chunks.stream().limit(MiConstants.ADVANCE_MAX_KNOWLEDGE_CHUNKS).toList();
    for (RetrievedChunk chunk : usable) {
      seq++;
      emitPlanStep(emit, seq, MiConstants.STEP_KIND_KNOWLEDGE, chunk.title());
      gathered
          .append(untrustedWrapper.wrap("knowledge", chunk.title(), chunk.content()))
          .append('\n');
    }
    if (!recalled.isEmpty()) {
      seq++;
      emitPlanStep(
          emit,
          seq,
          MiConstants.STEP_KIND_MEMORY,
          messageSource.getMessage("mi.advance.step.memory", null, "", locale));
      gathered.append(untrustedWrapper.wrap("memory", String.join("; ", recalled))).append('\n');
    }

    if (gathered.isEmpty()) {
      // Nothing was gathered, so there is nothing to compose from; composing anyway is how a model
      // starts inventing figures.
      return standard;
    }

    String material = gathered.toString();
    if (material.length() > MiConstants.ADVANCE_MAX_CONTEXT_CHARS) {
      material = material.substring(0, MiConstants.ADVANCE_MAX_CONTEXT_CHARS);
    }
    String composerPrompt =
        systemPrompt(agent, locale, null)
            + "\n"
            + messageSource.getMessage("mi.prompt.compose", null, "", locale)
            + "\n"
            + material;

    try {
      ModelReply composed =
          modelGateway.chat(
              agent, composerPrompt, List.of(), question, List.of(), locale);
      if (composed != null && composed.text() != null && !composed.text().isBlank()) {
        return composed.text();
      }
      log.debug("advance composition returned no text; falling back to the standard answer");
    } catch (RuntimeException e) {
      // A composer outage must not cost the customer the answer standard mode would have given.
      log.warn("advance composition failed, using the standard answer: {}", e.getMessage());
    }
    return standard;
  }

  private void emitPlanStep(
      BiConsumer<String, Object> emit, int seq, String kind, String label) {
    emit.accept(
        MiConstants.SSE_PLAN_STEP,
        new com.app.dto.response.MiResponses.TurnStepDto(
            seq, kind, label, MiConstants.STEP_DONE));
  }

  /** A localised name for the read, so the app never has to invent one. */
  private String toolLabel(ToolResult toolResult, Locale locale) {
    String key = "mi.advance.step.tool." + toolResult.code();
    return messageSource.getMessage(key, null, toolResult.code(), locale);
  }

  /** Strips anything instruction-like out of text that is about to reach the model. */
  private String screened(String text) {
    return screening.chunkIsSafe(text) ? text : "";
  }

  private List<ChipDto> chips(AgentSpec agent, ToolResult toolResult, Locale locale) {
    int max =
        agent.guardrails() == null || agent.guardrails().maxChipCount() <= 0
            ? MiConstants.SUGGESTION_COUNT
            : agent.guardrails().maxChipCount();
    return suggestions(agent.code(), locale).stream()
        .limit(max)
        .map(suggestion -> new ChipDto(suggestion.text(), suggestion.prompt()))
        .toList();
  }

  private List<SuggestionDto> suggestions(String agentCode, Locale locale) {
    AgentSpec agent = registry.agent(agentCode);
    if (agent.quickPrompts() != null && !agent.quickPrompts().isEmpty()) {
      return agent.quickPrompts().stream()
          .filter(QuickPrompt::enabled)
          .sorted(java.util.Comparator.comparingInt(QuickPrompt::sort))
          .limit(MiConstants.SUGGESTION_COUNT)
          .map(QuickPrompt::text)
          .map(text -> text.forLocale(locale))
          .map(text -> new SuggestionDto(text, text, "sparkle"))
          .toList();
    }
    // Before the CMS has quick prompts, the four the design names on screen 06.
    return List.of("transfer", "spending", "deposit", "bills").stream()
        .map(
            key -> {
              String text = messageSource.getMessage("mi.suggest." + key, null, locale);
              return new SuggestionDto(text, text, "sparkle");
            })
        .toList();
  }

  private Conversation createConversation(UUID customerId, String title) {
    Conversation conversation = new Conversation();
    conversation.setCustomerId(customerId);
    conversation.setTitle(title);
    conversation.setActiveAgentCode(AgentCodes.GENERAL);
    conversation.setLastMessageAt(java.time.Instant.now());
    return conversations.save(conversation);
  }

  private Message persistUser(Conversation conversation, UUID customerId, String text) {
    Message message = new Message();
    message.setConversationId(conversation.getId());
    message.setCustomerId(customerId);
    message.setRole(MiConstants.ROLE_USER);
    message.setKind(MiConstants.KIND_TEXT);
    message.setText(text);
    message.setSeq(messages.maxSeq(conversation.getId()) + 1);
    Message saved = messages.save(message);
    conversation.setLastMessageAt(saved.getCreatedAt());
    conversations.save(conversation);
    return saved;
  }

  private MessageDto emitText(
      Conversation conversation,
      UUID customerId,
      String text,
      List<com.app.dto.response.MiResponses.CitationDto> citationList,
      List<ChipDto> chipList,
      String agentCode,
      String safety,
      BiConsumer<String, Object> emit) {

    Message message = new Message();
    message.setConversationId(conversation.getId());
    message.setCustomerId(customerId);
    message.setRole(MiConstants.ROLE_MI);
    message.setKind(MiConstants.KIND_TEXT);
    message.setText(text);
    message.setAgentCode(agentCode);
    message.setSafety(safety);
    message.setCitations(objectMapper.writeValueAsString(citationList));
    message.setChips(objectMapper.writeValueAsString(chipList));
    message.setSeq(messages.maxSeq(conversation.getId()) + 1);
    Message saved = messages.save(message);

    MessageDto dto = toDto(saved);
    emit.accept(MiConstants.SSE_MESSAGE_MI, dto);
    if (!chipList.isEmpty()) {
      emit.accept(MiConstants.SSE_CHIPS, Map.of("messageId", saved.getId(), "chips", chipList));
    }
    return dto;
  }

  private void persistBlock(Conversation conversation, UUID customerId, MessageDto block) {
    Message message = new Message();
    message.setConversationId(conversation.getId());
    message.setCustomerId(customerId);
    message.setRole(MiConstants.ROLE_MI);
    message.setKind(block.kind());
    message.setAgentCode(block.agent());
    message.setPayload(objectMapper.writeValueAsString(block));
    message.setSeq(messages.maxSeq(conversation.getId()) + 1);
    messages.save(message);
  }

  private String eventFor(String kind) {
    return switch (kind) {
      case MiConstants.KIND_CARD -> MiConstants.SSE_CARD;
      case MiConstants.KIND_CHART -> MiConstants.SSE_CHART;
      case MiConstants.KIND_STEPS -> MiConstants.SSE_STEPS;
      default -> MiConstants.SSE_MESSAGE_MI;
    };
  }

  private MessageDto toDto(Message message) {
    return toDto(message, null);
  }

  /**
   * The user turn echoes the caller's {@code clientMessageId} so the app can replace its
   * optimistic bubble instead of rendering the message twice (mi-assistant-service-api.md §4b).
   */
  private MessageDto toDto(Message message, UUID clientMessageId) {
    return new MessageDto(
        message.getId(),
        clientMessageId,
        message.getRole(),
        message.getKind(),
        message.getText(),
        payload(message, com.app.dto.response.MiResponses.ProposalCardDto.class,
            MiConstants.KIND_CARD),
        payload(message, com.app.dto.response.MiResponses.ChartDto.class, MiConstants.KIND_CHART),
        payload(message, com.app.dto.response.MiResponses.StepsDto.class, MiConstants.KIND_STEPS),
        objectMapper.readValue(message.getChips(), new TypeReference<List<ChipDto>>() {}),
        objectMapper.readValue(
            message.getCitations(),
            new TypeReference<List<com.app.dto.response.MiResponses.CitationDto>>() {}),
        message.getAgentCode(),
        message.getSafety(),
        message.getSeq(),
        message.getCreatedAt());
  }

  @SuppressWarnings("unchecked")
  private <T> T payload(Message message, Class<T> type, String kind) {
    if (!kind.equals(message.getKind()) || message.getPayload() == null) {
      return null;
    }
    Map<String, Object> stored =
        objectMapper.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {});
    Object inner =
        stored.get(
            switch (kind) {
              case MiConstants.KIND_CARD -> "card";
              case MiConstants.KIND_CHART -> "chart";
              default -> "steps";
            });
    return inner == null ? null : objectMapper.convertValue(inner, type);
  }
}
