package com.app.service.impl;

import com.app.constant.MiConstants;
import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.RetrievedChunk;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.dto.response.MiResponses.ChartBar;
import com.app.dto.response.MiResponses.ChartDto;
import com.app.dto.response.MiResponses.MessageDto;
import com.app.dto.response.MiResponses.ProposalCardDto;
import com.app.dto.response.MiResponses.StepsDto;
import com.app.model.BaseEntity;
import com.app.model.Conversation;
import com.app.service.client.PeerClients.AccountClient;
import com.app.service.ui.BlockAssembler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class BlockAssemblerImpl implements BlockAssembler {

  private final MessageSource messages;
  private final ObjectMapper objectMapper;

  public BlockAssemblerImpl(MessageSource messages, ObjectMapper objectMapper) {
    this.messages = messages;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<MessageDto> assemble(
      Conversation conversation,
      UUID customerId,
      ToolResult toolResult,
      List<RetrievedChunk> chunks,
      boolean grounded,
      Locale locale) {

    List<MessageDto> blocks = new ArrayList<>();

    if (toolResult != null && toolResult.ok() && toolResult.data() instanceof ProposalCardDto card) {
      blocks.add(block(conversation, MiConstants.KIND_CARD, card, null, null, locale));
    }

    if (toolResult != null
        && ToolCodes.GET_SPENDING_SUMMARY.equals(toolResult.code())
        && toolResult.ok()) {
      ChartDto chart = chart(toolResult.data(), locale);
      if (chart != null) {
        blocks.add(block(conversation, MiConstants.KIND_CHART, null, chart, null, locale));
      }
    }

    // Guided action belongs to an answer that came from the knowledge base. When a data tool
    // answered — the loan balance, the due bills — the customer asked what, not how, and steps
    // under the reply are noise. And when the model answered freely with no citation at all,
    // `chunks` is just RAG context it may not have used — attaching its howto chunk's guide
    // glues an unrelated "how to transfer" card onto a "who are you" answer, which is exactly
    // what happened live with zero citations logged for that turn.
    boolean answeredFromKnowledge =
        grounded
            && (toolResult == null || ToolCodes.SEARCH_KNOWLEDGE.equals(toolResult.code()));
    chunks.stream()
        .limit(1)
        .filter(chunk -> answeredFromKnowledge)
        .filter(chunk -> MiConstants.COLLECTION_HOWTO.equals(chunk.collectionCode()))
        .filter(chunk -> chunk.deepLink() != null && !chunk.deepLink().isBlank())
        .findFirst()
        .ifPresent(
            chunk ->
                blocks.add(
                    block(
                        conversation,
                        MiConstants.KIND_STEPS,
                        null,
                        null,
                        steps(chunk, locale),
                        locale)));

    return blocks;
  }

  private ChartDto chart(Object data, Locale locale) {
    AccountClient.SpendingSummary summary =
        objectMapper.convertValue(data, AccountClient.SpendingSummary.class);
    if (summary == null || summary.categories() == null || summary.categories().isEmpty()) {
      return null;
    }
    List<ChartBar> bars =
        summary.categories().stream()
            .map(
                category ->
                    new ChartBar(category.name(), category.pct(), category.amount(),
                        category.color()))
            .toList();
    return new ChartDto(
        messages.getMessage("mi.chart.spending", new Object[] {summary.month()},
            "Spending", locale),
        bars);
  }

  private StepsDto steps(RetrievedChunk chunk, Locale locale) {
    List<String> lines =
        chunk.content().lines()
            .map(String::strip)
            .filter(line -> line.matches("^(\\d+[.)]|[-*])\\s+.*"))
            .map(line -> line.replaceFirst("^(\\d+[.)]|[-*])\\s+", ""))
            .limit(5)
            .toList();
    if (lines.isEmpty()) {
      lines = List.of(chunk.section() == null ? chunk.title() : chunk.section());
    }
    String label =
        messages.getMessage(
            "mi.steps.open", new Object[] {chunk.title()}, chunk.title(), locale);
    return new StepsDto(lines, chunk.deepLink(), label);
  }

  private MessageDto block(
      Conversation conversation,
      String kind,
      ProposalCardDto card,
      ChartDto chart,
      StepsDto steps,
      Locale locale) {
    return new MessageDto(
        BaseEntity.newId(),
        null,
        MiConstants.ROLE_MI,
        kind,
        null,
        card,
        chart,
        steps,
        List.of(),
        List.of(),
        conversation.getActiveAgentCode(),
        MiConstants.SAFETY_OK,
        0,
        Instant.now());
  }
}
