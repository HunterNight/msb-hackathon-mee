package com.app.service.impl;

import com.app.constant.ErrorCode;
import com.app.exception.BusinessException;
import com.app.service.rag.EmbeddingService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Embeddings from any OpenAI-compatible {@code /embeddings} endpoint — GreenNode, LM Studio or
 * Ollama. By default it shares the chat model's endpoint and credential ({@code LLM_BASE_URL},
 * {@code LLM_API_KEY}, {@code LLM_EMBEDDING_MODEL}); set the {@code MI_EMBEDDING_*} variables only
 * to point embeddings somewhere else.
 *
 * <p>This is what makes the knowledge base worth having. The hashing fallback matches character
 * shapes, so "phí trễ hạn" and "hạng thành viên" can land near each other; a trained model puts
 * them where they belong. The dimension must match the {@code document_chunk.embedding} column.
 *
 * <p>{@code ollama} is still accepted as a provider value so existing deployments keep working; it
 * was never Ollama-specific, only ever an OpenAI-shaped HTTP call.
 */
@Service
@ConditionalOnExpression(
    "'${app.mi.embedding.provider:local}' == 'openai'"
        + " or '${app.mi.embedding.provider:local}' == 'greennode'"
        + " or '${app.mi.embedding.provider:local}' == 'ollama'")
public class OpenAiEmbeddingService implements EmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingService.class);

  private final RestClient client;
  private final String model;
  private final int dimension;

  public OpenAiEmbeddingService(
      @Value("${app.mi.embedding.base-url}") String baseUrl,
      @Value("${app.mi.embedding.api-key:}") String apiKey,
      @Value("${app.mi.embedding.model}") String model,
      @Value("${app.mi.embedding.dimension}") int dimension,
      @Value("${app.mi.embedding.timeout-ms:30000}") long timeoutMs) {

    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    factory.setReadTimeout(Duration.ofMillis(timeoutMs));

    RestClient.Builder builder =
        RestClient.builder()
            .requestFactory(factory)
            .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
    // A hosted endpoint rejects an unauthenticated call; a local one ignores the header. Without
    // this, moving embeddings onto the chat endpoint fails with 401 on every document.
    if (apiKey != null && !apiKey.isBlank()) {
      builder = builder.defaultHeader("Authorization", "Bearer " + apiKey);
    }
    this.client = builder.build();
    this.model = model;
    this.dimension = dimension;
    // The key is never logged, only whether one is in use.
    log.info(
        "Mi embeddings: {} (model={}, dim={}, authenticated={})",
        baseUrl,
        model,
        dimension,
        apiKey != null && !apiKey.isBlank());
  }

  @Override
  @SuppressWarnings("unchecked")
  public float[] embed(String text) {
    String input = text == null ? "" : text;
    try {
      Map<String, Object> body =
          client
              .post()
              .uri("/embeddings")
              .body(Map.of("model", model, "input", input))
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});

      List<Map<String, Object>> data =
          body == null ? List.of() : (List<Map<String, Object>>) body.get("data");
      if (data == null || data.isEmpty()) {
        throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
      }
      List<Number> vector = (List<Number>) data.get(0).get("embedding");
      if (vector == null || vector.size() != dimension) {
        // A mismatch here would silently poison the index, so it fails loudly instead.
        log.error(
            "embedding model {} returned {} dimensions, column expects {}",
            model,
            vector == null ? 0 : vector.size(),
            dimension);
        throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
      }
      float[] out = new float[dimension];
      for (int i = 0; i < dimension; i++) {
        out[i] = vector.get(i).floatValue();
      }
      return out;
    } catch (BusinessException e) {
      throw e;
    } catch (RuntimeException e) {
      log.warn("embedding call failed: {}", e.getMessage());
      throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE);
    }
  }
}
