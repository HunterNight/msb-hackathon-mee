package com.app.service.client;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * A failure here must not fail the ingest: the conversation summary is an enrichment, and the
 * event is still worth processing without it.
 */
@Component
public class RestMiTranscriptClient implements MiTranscriptClient {

  private static final Logger log = LoggerFactory.getLogger(RestMiTranscriptClient.class);
  private static final String PATH = "/internal/conversations/{id}/masked-transcript";

  private final RestClient miClient;
  private final ObjectMapper objectMapper;

  public RestMiTranscriptClient(RestClient miClient, ObjectMapper objectMapper) {
    this.miClient = miClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<String> maskedTranscript(String conversationId) {
    return fetch(conversationId)
        .map(
            body -> {
              Object turns = body.get("turns");
              return turns == null
                  ? List.<String>of()
                  : objectMapper.convertValue(turns, new TypeReference<List<String>>() {});
            })
        .orElse(List.of());
  }

  @Override
  public List<ResolvedAlias> aliases(String conversationId) {
    return fetch(conversationId)
        .map(
            body -> {
              Object aliases = body.get("aliases");
              return aliases == null
                  ? List.<ResolvedAlias>of()
                  : objectMapper.convertValue(aliases, new TypeReference<List<ResolvedAlias>>() {});
            })
        .orElse(List.of());
  }

  private java.util.Optional<Map<String, Object>> fetch(String conversationId) {
    try {
      Map<String, Object> envelope =
          miClient
              .get()
              .uri(PATH, conversationId)
              .retrieve()
              .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
      if (envelope == null) {
        return java.util.Optional.empty();
      }
      Object data = envelope.get("data");
      return data == null
          ? java.util.Optional.empty()
          : java.util.Optional.of(
              objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {}));
    } catch (Exception e) {
      log.warn("masked transcript unavailable for conversation {}", conversationId);
      return java.util.Optional.empty();
    }
  }
}
