package com.app.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.app.dto.request.MiRequests.SendMessageRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Deserialising a chat turn.
 *
 * <p>Found in the running service: the app began sending {@code mode} before the backend knew the
 * field, and Jackson rejected the whole body with "Unrecognized property" — a 400 on every message,
 * the entire chat feature down for a purely additive change. These tests pin both halves of the fix:
 * the field exists, and an unknown one no longer takes the turn down with it.
 *
 * <p>The mapper here is configured strictly on purpose, mirroring
 * {@code spring.jackson.deserialization.fail-on-unknown-properties: true}, so the tolerance being
 * asserted is the DTO's own and not an artefact of a lenient test mapper.
 */
class SendMessageRequestTest {

  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  @Test
  @DisplayName("The advance-mode field the app sends is accepted")
  void acceptsMode() throws Exception {
    SendMessageRequest request =
        mapper.readValue(
            "{\"text\":\"số dư của tôi\",\"source\":\"TYPED\",\"mode\":\"ADVANCE\"}",
            SendMessageRequest.class);

    assertThat(request.mode()).isEqualTo("ADVANCE");
    assertThat(request.modeOrDefault()).isEqualTo("ADVANCE");
  }

  @Test
  @DisplayName("A turn without a mode defaults to the one-hop standard turn")
  void defaultsToStandard() throws Exception {
    SendMessageRequest request =
        mapper.readValue("{\"text\":\"xin chào\"}", SendMessageRequest.class);

    assertThat(request.mode()).isNull();
    assertThat(request.modeOrDefault()).isEqualTo("STANDARD");
    assertThat(request.sourceOrDefault()).isEqualTo("TYPED");
  }

  @Test
  @DisplayName("A field this backend has never heard of does not fail the turn")
  void ignoresUnknownFields() {
    // The exact failure mode observed: a newer client, an older backend, and every message rejected.
    assertThatCode(
            () ->
                mapper.readValue(
                    "{\"text\":\"hi\",\"someFutureField\":123,\"anotherOne\":{\"a\":1}}",
                    SendMessageRequest.class))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Screen context still deserialises, including an unknown field inside it")
  void parsesScreenContext() throws Exception {
    SendMessageRequest request =
        mapper.readValue(
            "{\"text\":\"trả trước 20 triệu\",\"context\":{\"screen\":\"lending.detail\","
                + "\"entityType\":\"LOAN\",\"entityId\":\"0192aaaa-0000-7000-8000-000000000001\","
                + "\"futureField\":true}}",
            SendMessageRequest.class);

    assertThat(request.context()).isNotNull();
    assertThat(request.context().screen()).isEqualTo("lending.detail");
    assertThat(request.context().entityType()).isEqualTo("LOAN");
    assertThat(request.context().entityId()).isNotNull();
  }

  @Test
  @DisplayName("Tolerance does not extend to dropping the fields that carry meaning")
  void stillReadsTheFieldsItKnows() throws Exception {
    SendMessageRequest request =
        mapper.readValue(
            "{\"text\":\"chuyển 2 triệu\",\"source\":\"CHIP\",\"mode\":\"STANDARD\","
                + "\"clientMessageId\":\"0192bbbb-0000-7000-8000-000000000002\"}",
            SendMessageRequest.class);

    assertThat(request.text()).isEqualTo("chuyển 2 triệu");
    assertThat(request.source()).isEqualTo("CHIP");
    assertThat(request.clientMessageId()).isNotNull();
  }
}
