package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.app.constant.AgentCodes;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.Guardrails;
import com.app.dto.internal.MiInternal.LocalizedText;
import com.app.dto.internal.MiInternal.RetrievedChunk;
import com.app.repository.ConversationRepository;
import com.app.repository.MessageRepository;
import com.app.service.agent.AgentRegistry;
import com.app.service.agent.GuardrailService;
import com.app.service.agent.RouterService;
import com.app.service.client.CmsClient;
import com.app.service.memory.MemoryService;
import com.app.service.memory.WorkingMemory;
import com.app.service.model.ModelGateway;
import com.app.service.rag.CitationPostProcessor;
import com.app.service.rag.RetrievalService;
import com.app.service.security.InputScreeningService;
import com.app.service.security.PiiRedactor;
import com.app.service.security.UntrustedWrapper;
import com.app.service.tool.ToolRegistry;
import com.app.service.ui.BlockAssembler;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.ObjectMapper;

/**
 * Which reply a customer gets when the knowledge base has nothing for them.
 *
 * <p>Before this was separated, a search that ran across every collection and matched nothing
 * answered "mình chưa hiểu rõ ý bạn" — Mi telling a customer who asked a perfectly clear question
 * that they were unclear, and then offering general-agent actions on a domain agent's turn.
 */
class ChatServiceKnowledgeAnswerTest {

  private RetrievalService retrieval;
  private ChatServiceImpl service;

  private static AgentSpec agent(String code) {
    return new AgentSpec(
        code,
        new LocalizedText("Mi", "Mi"),
        AgentCodes.DOMAIN_GENERAL,
        true,
        1,
        new LocalizedText("", ""),
        null,
        BigDecimal.valueOf(0.2),
        List.of(),
        List.of(),
        new Guardrails(List.of(), "k", null, true, 4, false),
        List.of(),
        null,
        "test");
  }

  private static RetrievedChunk chunk(double score) {
    return new RetrievedChunk(
        UUID.randomUUID(), "Biểu phí thẻ", "Phí", "Phí thường niên là …", null, "card", score);
  }

  @BeforeEach
  void setUp() {
    retrieval = Mockito.mock(RetrievalService.class);

    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("i18n/messages");
    messages.setDefaultEncoding(StandardCharsets.UTF_8.name());

    service =
        new ChatServiceImpl(
            Mockito.mock(ConversationRepository.class),
            Mockito.mock(MessageRepository.class),
            Mockito.mock(AgentRegistry.class),
            Mockito.mock(RouterService.class),
            Mockito.mock(ModelGateway.class),
            Mockito.mock(ToolRegistry.class),
            Mockito.mock(GuardrailService.class),
            Mockito.mock(InputScreeningService.class),
            Mockito.mock(PiiRedactor.class),
            Mockito.mock(CitationPostProcessor.class),
            retrieval,
            Mockito.mock(WorkingMemory.class),
            Mockito.mock(MemoryService.class),
            Mockito.mock(BlockAssembler.class),
            Mockito.mock(CmsClient.class),
            messages,
            Mockito.mock(ToolNarrator.class),
            Mockito.mock(com.app.service.model.VrmComposer.class),
            new UntrustedWrapper(),
            new ObjectMapper(),
            true,
            true);
  }

  @Test
  @DisplayName("A search that found nothing does not tell the customer they were unclear")
  void emptyRetrievalDoesNotBlameTheCustomer() {
    when(retrieval.belowThreshold(Mockito.anyList())).thenReturn(true);

    String reply = service.knowledgeAnswer(agent(AgentCodes.GENERAL), List.of(), Locale.ENGLISH);

    assertThat(reply).isNotBlank();
    // The exact wording of the old failure, which must no longer appear for a coverage gap.
    assertThat(reply).doesNotContain("didn't quite get that");
    // And it must offer a way forward rather than dead-ending.
    assertThat(reply).contains("1900 6083");
  }

  @Test
  @DisplayName("A weak match is treated as no answer, not served as if it were one")
  void belowThresholdIsTreatedAsNoAnswer() {
    when(retrieval.belowThreshold(Mockito.anyList())).thenReturn(true);

    String reply =
        service.knowledgeAnswer(agent(AgentCodes.GENERAL), List.of(chunk(0.37)), Locale.ENGLISH);

    // The chunk's own text must not leak through as the answer.
    assertThat(reply).doesNotContain("Phí thường niên");
    assertThat(reply).contains("1900 6083");
  }

  @Test
  @DisplayName("A good match is answered from the document, with its citation tag intact")
  void aboveThresholdAnswersFromTheDocument() {
    when(retrieval.belowThreshold(Mockito.anyList())).thenReturn(false);
    RetrievedChunk best = chunk(0.88);

    String reply =
        service.knowledgeAnswer(agent(AgentCodes.GENERAL), List.of(best), Locale.ENGLISH);

    assertThat(reply).contains("Phí thường niên").contains("[doc:" + best.docId() + "]");
  }

  @ParameterizedTest(name = "the {0} agent answers on its own topic, mentioning {1}")
  @CsvSource({
    "loan, lending",
    "card, card",
    "saving, savings",
    "payment, payment"
  })
  @DisplayName("At Level 3 the no-answer reply stays inside the domain handling the turn")
  void domainAgentsStayOnTopic(String agentCode, String expectedTopic) {
    when(retrieval.belowThreshold(Mockito.anyList())).thenReturn(true);

    String reply = service.knowledgeAnswer(agent(agentCode), List.of(), Locale.ENGLISH);
    String generalReply =
        service.knowledgeAnswer(agent(AgentCodes.GENERAL), List.of(), Locale.ENGLISH);

    assertThat(reply).containsIgnoringCase(expectedTopic);
    // The whole point of per-agent copy: the loan agent must not answer with the generic text that
    // suggests transfers and deposits.
    assertThat(reply).isNotEqualTo(generalReply);
  }

  @Test
  @DisplayName("An agent with no copy of its own still gets the generic reply, not a raw key")
  void unknownAgentFallsBackToGenericCopy() {
    when(retrieval.belowThreshold(Mockito.anyList())).thenReturn(true);

    String reply = service.knowledgeAnswer(agent("insurance"), List.of(), Locale.ENGLISH);

    assertThat(reply).doesNotContain("mi.reply.noAnswer").contains("1900 6083");
  }
}
