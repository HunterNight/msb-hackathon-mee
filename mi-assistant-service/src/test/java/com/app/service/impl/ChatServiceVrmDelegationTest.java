package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.constant.AgentCodes;
import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.Guardrails;
import com.app.dto.internal.MiInternal.LocalizedText;
import com.app.dto.internal.MiInternal.ModelReply;
import com.app.dto.internal.MiInternal.RetrievedChunk;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.repository.ConversationRepository;
import com.app.repository.MessageRepository;
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
import com.app.service.tool.ToolRegistry;
import com.app.service.ui.BlockAssembler;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.ObjectMapper;

/**
 * The delegation boundary: which turns the VRM agent is allowed to word, and what happens when it
 * cannot.
 *
 * <p>Driven through {@code composeDelegating} by reflection rather than through a whole turn. The
 * method <em>is</em> the decision, and a test that drove the full pipeline would need the model
 * gateway, repositories and SSE plumbing to agree before it even reached the branch — which is how an
 * earlier version of this suite came to pass while never executing the line it claimed to cover.
 */
class ChatServiceVrmDelegationTest {

  private static final UUID CUSTOMER = UUID.randomUUID();
  private static final UUID CONVERSATION = UUID.randomUUID();
  private static final UUID DOC = UUID.randomUUID();
  private static final String QUESTION = "lãi suất 6 tháng?";

  private VrmComposer vrm;
  private RetrievalService retrieval;
  private ChatServiceImpl service;
  private Method composeDelegating;

  private static AgentSpec agent() {
    return new AgentSpec(
        AgentCodes.SAVING,
        new LocalizedText("Mi", "Mi"),
        AgentCodes.DOMAIN_SAVING,
        true,
        1,
        new LocalizedText("Bạn là Mi.", "You are Mi."),
        null,
        BigDecimal.valueOf(0.2),
        List.of(),
        List.of(),
        new Guardrails(List.of(), "k", null, true, 4, false),
        List.of(),
        null,
        "test");
  }

  /** A passage good enough to answer from — the shape that makes delegation eligible. */
  private static RetrievedChunk chunk() {
    return new RetrievedChunk(
        DOC, "Lãi suất tiền gửi", "6 tháng", "Kỳ hạn 6 tháng: 5,2%/năm.", null, "saving", 0.91);
  }

  @BeforeEach
  void setUp() throws Exception {
    vrm = Mockito.mock(VrmComposer.class);
    retrieval = Mockito.mock(RetrievalService.class);
    when(retrieval.belowThreshold(anyList())).thenReturn(false);

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
            vrm,
            new UntrustedWrapper(),
            new ObjectMapper(),
            true,
            false);

    composeDelegating =
        ChatServiceImpl.class.getDeclaredMethod(
            "composeDelegating",
            AgentSpec.class,
            ModelReply.class,
            ToolResult.class,
            List.class,
            List.class,
            String.class,
            UUID.class,
            UUID.class,
            Locale.class);
    composeDelegating.setAccessible(true);
  }

  private String invoke(ToolResult toolResult, List<RetrievedChunk> chunks) throws Exception {
    return (String)
        composeDelegating.invoke(
            service,
            agent(),
            new ModelReply(null, null, Map.of(), "rules"),
            toolResult,
            chunks,
            List.<String>of(),
            QUESTION,
            CUSTOMER,
            CONVERSATION,
            Locale.forLanguageTag("vi"));
  }

  @Test
  @DisplayName("A grounded question with no tool is worded by the agent, and keeps mi's citation")
  void delegatesGroundedQuestion() throws Exception {
    when(vrm.enabled()).thenReturn(true);
    when(vrm.compose(any(), any(), anyList(), any(), any(), any()))
        .thenReturn(Optional.of("Lãi suất kỳ hạn 6 tháng là 5,2%/năm."));

    String text = invoke(null, List.of(chunk()));

    assertThat(text).contains("5,2%/năm");
    // Without the marker the answer arrives with no source chip and the customer cannot check it.
    assertThat(text).contains("[doc:" + DOC + "]");
  }

  @Test
  @DisplayName("mi tells the agent the domain it routed, so the agent skips its own router call")
  void passesTheRoutedDomain() throws Exception {
    when(vrm.enabled()).thenReturn(true);
    when(vrm.compose(any(), any(), anyList(), any(), any(), any())).thenReturn(Optional.of("ok"));

    invoke(null, List.of(chunk()));

    // agent.code() is already the lowercase name the agent's DOMAINS use, so the two vocabularies
    // line up without a mapping table — worth pinning, because a mismatch would not fail anything:
    // the agent would quietly fall back to its own router and the latency saving would vanish.
    verify(vrm)
        .compose(
            eq(AgentCodes.SAVING),
            eq(QUESTION),
            anyList(),
            eq(CUSTOMER),
            eq(CONVERSATION),
            any());
  }

  @Test
  @DisplayName("A turn where a tool ran is never handed over: mi narrates the customer's own money")
  void neverDelegatesWhenAToolRan() throws Exception {
    when(vrm.enabled()).thenReturn(true);
    ToolResult ran =
        new ToolResult(ToolCodes.GET_ACCOUNT_SUMMARY, true, Map.of("balance", 1_000_000), null, 12);

    invoke(ran, List.of(chunk()));

    verify(vrm, never()).compose(any(), any(), anyList(), any(), any(), any());
  }

  @Test
  @DisplayName("A knowledge lookup is still delegated: it is retrieval, not an action")
  void delegatesAfterSearchKnowledge() throws Exception {
    when(vrm.enabled()).thenReturn(true);
    when(vrm.compose(any(), any(), anyList(), any(), any(), any()))
        .thenReturn(Optional.of("Lãi suất kỳ hạn 6 tháng là 5,2%/năm."));

    // Found live: the router picks search_knowledge for most questions, so treating every tool alike
    // disabled delegation on exactly the path it was built for. The turn answered in 0.4s from mi's
    // own passage and the agent was never called.
    ToolResult lookup = new ToolResult(ToolCodes.SEARCH_KNOWLEDGE, true, Map.of(), null, 99);

    String text = invoke(lookup, List.of(chunk()));

    verify(vrm).compose(any(), any(), anyList(), any(), any(), any());
    assertThat(text).contains("5,2%/năm");
  }

  @Test
  @DisplayName("With nothing retrieved the agent is not asked, so it cannot invent a rate")
  void neverDelegatesWithoutGrounding() throws Exception {
    when(vrm.enabled()).thenReturn(true);

    invoke(null, List.of());

    verify(vrm, never()).compose(any(), any(), anyList(), any(), any(), any());
  }

  @Test
  @DisplayName("A weak retrieval match is not grounding either")
  void neverDelegatesBelowThreshold() throws Exception {
    when(vrm.enabled()).thenReturn(true);
    when(retrieval.belowThreshold(anyList())).thenReturn(true);

    invoke(null, List.of(chunk()));

    verify(vrm, never()).compose(any(), any(), anyList(), any(), any(), any());
  }

  @Test
  @DisplayName("An agent that declines still leaves the customer with mi's own answer")
  void fallsBackWhenTheAgentDeclines() throws Exception {
    when(vrm.enabled()).thenReturn(true);
    when(vrm.compose(any(), any(), anyList(), any(), any(), any())).thenReturn(Optional.empty());

    String text = invoke(null, List.of(chunk()));

    assertThat(text).contains("5,2%/năm");
    assertThat(text).contains("[doc:" + DOC + "]");
  }

  @Test
  @DisplayName("Disabled, the agent is not called at all")
  void doesNothingWhenDisabled() throws Exception {
    when(vrm.enabled()).thenReturn(false);

    String text = invoke(null, List.of(chunk()));

    verify(vrm, never()).compose(any(), any(), anyList(), any(), any(), any());
    assertThat(text).isNotBlank();
  }
}
