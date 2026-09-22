package com.app.service.impl;

import com.app.constant.MiConstants;
import com.app.dto.internal.MiInternal.RetrievedChunk;
import com.app.service.model.VrmComposer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Calls the VRM agent's single {@code POST /invocations} entry point.
 *
 * <p>Everything about this class is arranged so a bad answer or a dead agent costs nothing: the call
 * is time-boxed, every failure returns {@link Optional#empty()}, and the caller composes the answer
 * itself when it does. The agent is never the only thing standing between a customer and a reply.
 */
@Slf4j
@Service
public class VrmComposerImpl implements VrmComposer {

  /**
   * How many of mi's passages to send. The agent's prompt is already long and the passages are the
   * bulk of it; the top few carry the answer, and a longer context is a slower call.
   */
  private static final int MAX_GROUNDING = 5;

  /** Enough of a passage to answer from, without turning one turn into a document upload. */
  private static final int MAX_BODY_CHARS = 1200;

  private final RestClient client;
  private final boolean enabled;

  public VrmComposerImpl(
      @Qualifier("vrmClient") RestClient client,
      @Value("${app.mi.vrm.enabled:false}") boolean enabled) {
    this.client = client;
    this.enabled = enabled;
  }

  @Override
  public boolean enabled() {
    return enabled;
  }

  @Override
  public Optional<String> compose(
      String agentCode,
      String question,
      List<RetrievedChunk> chunks,
      UUID customerId,
      UUID conversationId,
      Locale locale) {

    if (!enabled || question == null || question.isBlank() || chunks == null || chunks.isEmpty()) {
      return Optional.empty();
    }

    try {
      Map<String, Object> body =
          Map.of(
              "message", question,
              // mi has already classified the turn with its deterministic router, and the agent's own
              // router is a whole LLM call to produce one word. Telling it the domain removes that
              // call, which on the hosted model was 5-14 seconds of every turn.
              "domain", agentCode == null ? "general" : agentCode.toLowerCase(Locale.ROOT),
              // The agent's tools are mocks against no real account service. mi has already run the
              // real ones, so the agent is asked for wording only.
              "tools_disabled", true,
              "grounding", grounding(chunks));

      Map<?, ?> response =
          client
              .post()
              .uri("/invocations")
              .header(MiConstants.HDR_AGENTBASE_USER, String.valueOf(customerId))
              .header(MiConstants.HDR_AGENTBASE_SESSION, String.valueOf(conversationId))
              .body(body)
              .retrieve()
              .body(Map.class);

      if (response == null || !"success".equals(response.get("status"))) {
        log.warn(
            "vrm compose declined status={} conversation={}",
            response == null ? "no-body" : response.get("status"),
            conversationId);
        return Optional.empty();
      }

      Object text = response.get("response");
      String answer = text == null ? "" : String.valueOf(text).trim();
      if (answer.isEmpty()) {
        return Optional.empty();
      }
      log.debug("vrm composed conversation={} length={}", conversationId, answer.length());
      return Optional.of(answer);

    } catch (Exception e) {
      // Unreachable, timed out, 4xx, malformed body: all the same outcome, because the caller has a
      // working answer of its own either way. Logged at warn rather than rethrown for that reason.
      log.warn("vrm compose failed conversation={} error={}", conversationId, e.toString());
      return Optional.empty();
    }
  }

  /** mi's passages, trimmed, in the shape the agent's {@code grounding} field expects. */
  private List<Map<String, String>> grounding(List<RetrievedChunk> chunks) {
    List<Map<String, String>> out = new ArrayList<>();
    for (RetrievedChunk chunk : chunks.stream().limit(MAX_GROUNDING).toList()) {
      String content = chunk.content() == null ? "" : chunk.content();
      out.add(
          Map.of(
              "title", chunk.title() == null ? "" : chunk.title(),
              "body",
                  content.length() > MAX_BODY_CHARS ? content.substring(0, MAX_BODY_CHARS) : content,
              "docId", String.valueOf(chunk.docId()),
              "anchor", chunk.section() == null ? "" : chunk.section()));
    }
    return out;
  }
}
