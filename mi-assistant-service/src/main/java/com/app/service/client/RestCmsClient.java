package com.app.service.client;

import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.ModelProfile;
import com.app.dto.internal.MiInternal.RouterConfig;
import com.app.dto.internal.MiInternal.ToolSpec;
import com.app.dto.internal.MiInternal.TriggerRule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the CMS over its internal API. Every method degrades to an empty result rather than
 * throwing: a CMS outage must leave Mi answering from cache, not failing the turn.
 */
@Component
public class RestCmsClient implements CmsClient {

  private static final Logger log = LoggerFactory.getLogger(RestCmsClient.class);

  private final RestClient cmsClient;
  private final ObjectMapper objectMapper;

  public RestCmsClient(RestClient cmsClient, ObjectMapper objectMapper) {
    this.cmsClient = cmsClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<AgentSpec> agents() {
    return list("/internal/agents?enabled=true", new TypeReference<List<AgentSpec>>() {});
  }

  @Override
  public Optional<AgentSpec> agent(String code) {
    return one("/internal/agents/" + code, new TypeReference<AgentSpec>() {});
  }

  @Override
  public Optional<RouterConfig> router() {
    return one("/internal/router", new TypeReference<RouterConfig>() {});
  }

  @Override
  public List<ToolSpec> tools() {
    return list("/internal/tools", new TypeReference<List<ToolSpec>>() {});
  }

  @Override
  public List<TriggerRule> triggers() {
    return list("/internal/triggers", new TypeReference<List<TriggerRule>>() {});
  }

  @Override
  public List<ModelProfile> modelProfiles() {
    return list("/internal/model-profiles", new TypeReference<List<ModelProfile>>() {});
  }

  @Override
  public Optional<CmsDocument> currentDoc(UUID docId) {
    Optional<Map<String, Object>> envelope =
        one("/internal/docs/" + docId + "/current", new TypeReference<Map<String, Object>>() {});
    return envelope.map(this::toDocument);
  }

  @Override
  public List<UUID> publishedDocIds(String collectionCode) {
    String path =
        "/internal/docs?status=PUBLISHED"
            + (collectionCode == null ? "" : "&collection=" + collectionCode);
    List<Map<String, Object>> docs = list(path, new TypeReference<List<Map<String, Object>>>() {});
    return docs.stream()
        .map(doc -> doc.get("id"))
        .filter(java.util.Objects::nonNull)
        .map(id -> UUID.fromString(id.toString()))
        .toList();
  }

  @Override
  public void feedback(
      UUID conversationId,
      UUID messageId,
      String agentCode,
      String rating,
      String comment,
      UUID customerId) {
    try {
      cmsClient
          .post()
          .uri("/internal/feedback")
          .body(
              Map.of(
                  "conversationId", conversationId,
                  "messageId", messageId,
                  "agentCode", agentCode == null ? "general" : agentCode,
                  "rating", rating,
                  "comment", comment == null ? "" : comment,
                  "customerId", customerId))
          .retrieve()
          .toBodilessEntity();
    } catch (Exception e) {
      // Losing a thumbs-up must never fail the customer's request.
      log.warn("feedback not delivered to CMS for message {}", messageId);
    }
  }

  private <T> List<T> list(String path, TypeReference<List<T>> type) {
    return this.<List<T>>fetch(path).map(data -> objectMapper.convertValue(data, type))
        .orElse(List.of());
  }

  private <T> Optional<T> one(String path, TypeReference<T> type) {
    return this.<Object>fetch(path).map(data -> objectMapper.convertValue(data, type));
  }

  private <T> Optional<Object> fetch(String path) {
    try {
      Map<String, Object> envelope =
          cmsClient
              .get()
              .uri(path)
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
      return envelope == null ? Optional.empty() : Optional.ofNullable(envelope.get("data"));
    } catch (Exception e) {
      log.warn("cms unavailable for {}", path);
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  private CmsDocument toDocument(Map<String, Object> envelope) {
    Map<String, Object> doc = (Map<String, Object>) envelope.get("doc");
    Map<String, Object> version = (Map<String, Object>) envelope.get("version");
    List<CmsSection> sections =
        version == null
            ? List.of()
            : objectMapper.convertValue(
                version.getOrDefault("sections", List.of()),
                new TypeReference<List<CmsSection>>() {});
    return new CmsDocument(
        UUID.fromString(doc.get("id").toString()),
        String.valueOf(doc.get("collectionCode")),
        String.valueOf(doc.get("title")),
        String.valueOf(doc.getOrDefault("locale", "vi")),
        version == null ? 1 : Integer.parseInt(version.getOrDefault("version", 1).toString()),
        sections);
  }
}
