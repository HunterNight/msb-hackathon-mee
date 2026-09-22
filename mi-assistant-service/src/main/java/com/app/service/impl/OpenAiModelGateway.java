package com.app.service.impl;

import com.app.constant.AgentCodes;
import com.app.constant.MiConstants;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.ModelReply;
import com.app.dto.internal.MiInternal.RouteDecision;
import com.app.dto.internal.MiInternal.ToolSpec;
import com.app.service.model.ModelGateway;
import com.app.service.security.UntrustedWrapper;
import org.springframework.core.ParameterizedTypeReference;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The hosted gateway, speaking the OpenAI chat-completions dialect. That covers GreenNode AI
 * Platform, LM Studio, Ollama and anything else that implements {@code /chat/completions}, so the
 * only thing that changes between them is the base URL and the model name.
 *
 * <p>Tool selection stays the model's suggestion and nothing more: the orchestrator still validates
 * the arguments and the allow-list before anything runs (guideline 06 §4).
 */
@Service
@Primary
@ConditionalOnExpression(
    "'${app.mi.llm.provider:rules}' == 'openai' or '${app.mi.llm.provider:rules}' == 'greennode'")
public class OpenAiModelGateway implements ModelGateway {

  private static final Logger log = LoggerFactory.getLogger(OpenAiModelGateway.class);
  private static final String PROFILE_CHAT = "chat";
  private static final String PROFILE_ROUTER = "router";

  private static final String ROUTER_INSTRUCTION =
      """
      You classify one banking chat turn into exactly one domain.
      Domains: loan, card, saving, payment, general.
      Answer with JSON only, no prose, in this shape:
      {"domain":"<domain>","confidence":<0..1>,"summary":"<=25 words"}""";

  private final RestClient client;
  private final ObjectMapper mapper;
  private final RuleModelGateway rules;
  private final UntrustedWrapper untrusted;
  private final String chatModel;
  private final String routerModel;
  private final int maxTokens;

  public OpenAiModelGateway(
      ObjectMapper mapper,
      RuleModelGateway rules,
      UntrustedWrapper untrusted,
      @Value("${app.mi.llm.base-url}") String baseUrl,
      @Value("${app.mi.llm.api-key:}") String apiKey,
      @Value("${app.mi.llm.chat-model}") String chatModel,
      @Value("${app.mi.llm.router-model:}") String routerModel,
      @Value("${app.mi.llm.max-tokens:600}") int maxTokens,
      @Value("${app.mi.llm.timeout-ms:120000}") long timeoutMs) {

    // The JDK client negotiates HTTP/2 by default. Local model servers (LM Studio, Ollama) speak
    // HTTP/1.1 and fail the upgrade, which surfaces as an I/O error rather than a status code.
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofMillis(timeoutMs));

    RestClient.Builder builder =
        RestClient.builder().requestFactory(factory).baseUrl(stripTrailingSlash(baseUrl));
    // A local server needs no key; a hosted one does, and sending an empty header upsets some.
    if (!apiKey.isBlank()) {
      builder = builder.defaultHeader("Authorization", "Bearer " + apiKey);
    }
    this.client = builder.build();
    this.mapper = mapper;
    this.rules = rules;
    this.untrusted = untrusted;
    this.chatModel = chatModel;
    // Blank router model means "classify with the rules". A small model invents domain labels
    // outside the taxonomy, and a wrong hand-off costs the agent its tools — so routing is worth
    // keeping deterministic unless a capable model is named for it.
    this.routerModel = routerModel == null ? "" : routerModel.trim();
    this.maxTokens = maxTokens;
    log.info(
        "Mi model gateway: {} (chat={}, router={})",
        baseUrl,
        chatModel,
        this.routerModel.isBlank() ? "rules" : this.routerModel);
  }

  @Override
  public ModelReply chat(
      AgentSpec agent,
      String systemPrompt,
      List<Turn> history,
      String userTurn,
      List<ToolSpec> toolsAvailable,
      Locale locale) {

    boolean wantsTools = toolsAvailable != null && !toolsAvailable.isEmpty();
    if (wantsTools) {
      // A small local model (gemma3, phi, most 1B weights) is not reliable here even when it
      // accepts the request: found live, it called find_beneficiary with a blank recipientQuery
      // and no amount, and transfer-service's validation 400 reached the customer as "system
      // busy". Tool selection stays fully deterministic; only tool-free chat below ever reaches
      // the network model.
      return rules.chat(agent, systemPrompt, history, userTurn, toolsAvailable, locale);
    }

    // A handful of conversational turns are answered with fixed copy before ever reaching the
    // network — a small model asked to just chat (no tool it can reach for) is exactly where it
    // produced garbled Vietnamese in testing ("Cảm ơn Mi nhé", "Mi ơi bạn có thể làm gì?").
    java.util.Optional<ModelReply> smallTalk = rules.smallTalk(userTurn, locale);
    if (smallTalk.isPresent()) {
      return smallTalk.get();
    }

    // The agent may pin its own model in the CMS; otherwise the deployment default applies.
    String model = agent != null && agent.model() != null && !agent.model().isBlank()
        ? agent.model()
        : chatModel;

    List<Map<String, Object>> messages = new ArrayList<>();
    if (systemPrompt != null && !systemPrompt.isBlank()) {
      messages.add(Map.of("role", "system", "content", systemPrompt));
    }
    // Everything below the system message is data the customer or a previous turn produced, so it
    // is labelled before the model sees it (design §12.1). Without this the system prompt's rule
    // about `<untrusted>` blocks referred to a delimiter that never appeared in any prompt.
    //
    // Only this gateway needs it: RuleModelGateway never sends a prompt anywhere, so there is no
    // model there to mislead.
    for (Turn turn : history) {
      messages.add(
          Map.of(
              "role",
              roleOf(turn.role()),
              "content",
              untrusted.wrap("history", turn.text())));
    }
    messages.add(Map.of("role", "user", "content", untrusted.wrap("user", userTurn)));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("messages", messages);
    body.put("max_tokens", maxTokens);
    body.put(
        "temperature",
        agent != null && agent.temperature() != null
            ? agent.temperature().doubleValue()
            : MiConstants.LLM_TEMPERATURE_DEFAULT);

    // No tools are ever attached here — a turn that wants one already returned above — so there
    // is nothing for the model to call back with, only prose to read.
    Map<String, Object> message = firstMessage(post(body));
    String text = str(message.get("content")).strip();
    return new ModelReply(text.isBlank() ? null : text, null, Map.of(), PROFILE_CHAT);
  }

  @Override
  public RouteDecision classify(String text, List<Turn> history, Locale locale) {
    if (routerModel.isBlank()) {
      return rules.classify(text, history, locale);
    }
    Map<String, Object> body =
        Map.of(
            "model", routerModel,
            "messages",
                List.of(
                    Map.of("role", "system", "content", ROUTER_INSTRUCTION),
                    // The router is a model too, and a turn crafted to look like an instruction
                    // could otherwise steer which domain agent handles it.
                    Map.of("role", "user", "content", untrusted.wrap("user", text))),
            "temperature", 0,
            "max_tokens", 200,
            // Small models drift into prose when asked for JSON; this makes the server enforce it.
            "response_format", Map.of("type", "json_object"));

    String content;
    try {
      content = str(firstMessage(post(body)).get("content"));
    } catch (RuntimeException e) {
      // Routing is an optimisation: staying with the current agent is always safe.
      log.warn("router call failed, keeping the current agent: {}", e.getMessage());
      return new RouteDecision(AgentCodes.DOMAIN_GENERAL, 0d, summarise(text));
    }

    Map<String, Object> json = parseLenient(content);
    if (json == null) {
      log.warn("router returned no JSON object; content began: {}",
          content.length() > 80 ? content.substring(0, 80) : content);
      return new RouteDecision(AgentCodes.DOMAIN_GENERAL, 0d, summarise(text));
    }

    String domain = str(json.get("domain")).trim().toUpperCase(Locale.ROOT);
    double confidence = json.get("confidence") instanceof Number n ? n.doubleValue() : 0d;
    String summary = str(json.get("summary"));
    String resolved = AgentCodes.DOMAINS.contains(domain) ? domain : AgentCodes.DOMAIN_GENERAL;
    log.info("router decision: raw={} resolved={} confidence={}", domain, resolved, confidence);
    return new RouteDecision(
        resolved, Math.max(0d, Math.min(1d, confidence)), summary.isBlank() ? summarise(text) : summary);
  }

  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {};

  private Map<String, Object> post(Map<String, Object> body) {
    try {
      Map<String, Object> response =
          client.post().uri("/chat/completions").body(body).retrieve().body(JSON_OBJECT);
      if (response == null) {
        throw new IllegalStateException("empty response from the model endpoint");
      }
      return response;
    } catch (RestClientResponseException e) {
      // The model server's own message is the only thing that explains a rejected request.
      log.warn(
          "model endpoint returned {}: {}",
          e.getStatusCode(),
          e.getResponseBodyAsString().replace('\n', ' '));
      throw e;
    } catch (RuntimeException e) {
      log.warn("model call failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> firstMessage(Map<String, Object> response) {
    Object choices = response.get("choices");
    if (!(choices instanceof List<?> list) || list.isEmpty()) {
      throw new IllegalStateException("model returned no choices");
    }
    Object message = ((Map<String, Object>) list.get(0)).get("message");
    return message instanceof Map ? (Map<String, Object>) message : Map.of();
  }

  private static String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /** Models sometimes wrap JSON in prose or a code fence; take the first object either way. */
  private Map<String, Object> parseLenient(String content) {
    if (content == null) return null;
    int start = content.indexOf('{');
    int end = content.lastIndexOf('}');
    if (start < 0 || end <= start) return null;
    try {
      return mapper.readValue(content.substring(start, end + 1), MAP_TYPE);
    } catch (Exception e) {
      log.warn("router returned unparseable JSON");
      return null;
    }
  }

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private static String roleOf(String role) {
    return "user".equals(role) || "assistant".equals(role) || "system".equals(role) ? role : "user";
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String summarise(String text) {
    String trimmed = text == null ? "" : text.strip();
    int limit = MiConstants.HANDOFF_SUMMARY_TOKENS * MiConstants.CHARS_PER_TOKEN;
    return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit) + "…";
  }
}
