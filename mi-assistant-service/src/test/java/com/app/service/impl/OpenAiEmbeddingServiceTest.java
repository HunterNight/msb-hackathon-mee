package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.exception.BusinessException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Embeddings over an OpenAI-compatible endpoint.
 *
 * <p>The credential is the reason this test exists. The client previously sent no {@code
 * Authorization} header, which is invisible against a local Ollama and a 401 on every document the
 * moment embeddings are pointed at a hosted endpoint such as GreenNode.
 */
class OpenAiEmbeddingServiceTest {

  private WireMockServer server;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  /** An /embeddings response with a vector of the given width. */
  private void stubVectorOf(int size) {
    String vector = IntStream.range(0, size).mapToObj(i -> "0.01").reduce((a, b) -> a + "," + b).orElse("");
    server.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/embeddings"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"data\":[{\"embedding\":[" + vector + "]}]}")));
  }

  private OpenAiEmbeddingService service(String apiKey, int dimension) {
    return new OpenAiEmbeddingService(server.baseUrl(), apiKey, "bge-m3", dimension, 5000);
  }

  @Test
  @DisplayName("The API key is sent as a bearer token, so a hosted endpoint accepts the call")
  void sendsTheApiKey() {
    stubVectorOf(4);

    service("secret-key", 4).embed("xin chào");

    server.verify(
        WireMock.postRequestedFor(WireMock.urlPathEqualTo("/embeddings"))
            .withHeader("Authorization", WireMock.equalTo("Bearer secret-key")));
  }

  @Test
  @DisplayName("With no key configured no Authorization header is sent, which a local server needs")
  void omitsTheHeaderWhenNoKey() {
    stubVectorOf(4);

    service("", 4).embed("xin chào");

    server.verify(
        WireMock.postRequestedFor(WireMock.urlPathEqualTo("/embeddings"))
            .withoutHeader("Authorization"));
  }

  @Test
  @DisplayName("The configured model is what gets requested")
  void requestsTheConfiguredModel() {
    stubVectorOf(4);

    service("k", 4).embed("số dư của tôi");

    server.verify(
        WireMock.postRequestedFor(WireMock.urlPathEqualTo("/embeddings"))
            .withRequestBody(WireMock.containing("\"model\":\"bge-m3\"")));
  }

  @Test
  @DisplayName("A vector is returned at the configured width")
  void returnsTheVector() {
    stubVectorOf(8);

    float[] vector = service("k", 8).embed("hello");

    assertThat(vector).hasSize(8);
    assertThat(vector[0]).isEqualTo(0.01f);
  }

  /**
   * The failure that matters when swapping providers: a different model almost certainly has a
   * different width, and silently truncating it would poison the index rather than fail.
   */
  @Test
  @DisplayName("A width other than the column's is refused, not truncated")
  void refusesAWidthMismatch() {
    stubVectorOf(768);

    assertThatThrownBy(() -> service("k", 1024).embed("hello"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("An endpoint with no such model surfaces as an upstream failure")
  void modelNotFoundIsAnUpstreamFailure() {
    // Exactly what the VNG MaaS host answers today for every embedding model id.
    server.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/embeddings"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status_code\":404,\"message\":\"The requested model is not found\"}")));

    assertThatThrownBy(() -> service("k", 1024).embed("hello"))
        .isInstanceOf(BusinessException.class);
  }
}
