package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.constant.AgentCodes;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.Guardrails;
import com.app.dto.internal.MiInternal.LocalizedText;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The chat bubble is a plain {@code Text}, so anything markdown-shaped reaches the customer as
 * punctuation. Bold and italic were already handled; the VRM agent also answers rate and fee
 * questions with tables and headings, which arrived as rows of pipes with the columns collapsed — every
 * number present and none of it readable.
 */
class GuardrailServiceMarkdownTest {

  private GuardrailServiceImpl guardrails;
  private Method stripMarkdown;

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

  @BeforeEach
  void setUp() throws Exception {
    guardrails = new GuardrailServiceImpl();
    stripMarkdown = GuardrailServiceImpl.class.getDeclaredMethod("stripMarkdown", String.class);
    stripMarkdown.setAccessible(true);
  }

  private String strip(String reply) throws Exception {
    return (String) stripMarkdown.invoke(guardrails, reply);
  }

  @Test
  @DisplayName("A rate table becomes a readable line, keeping every figure")
  void flattensTables() throws Exception {
    String table =
        """
        | Kỳ hạn | Lãi suất |
        |---|---|
        | 6 tháng | 5,2%/năm |
        | 12 tháng | 5,6%/năm |
        """;

    String flat = strip(table);

    assertThat(flat).contains("Kỳ hạn · Lãi suất");
    assertThat(flat).contains("6 tháng · 5,2%/năm");
    assertThat(flat).contains("12 tháng · 5,6%/năm");
    // The rule row is pure formatting; leaving it in put a line of dashes in the middle of the answer.
    assertThat(flat).doesNotContain("---");
    assertThat(flat).doesNotContain("|");
  }

  @Test
  @DisplayName("Headings and blockquote markers are dropped, their text kept")
  void dropsHeadingAndQuoteMarkers() throws Exception {
    String flat = strip("## Lãi suất tiền gửi\n> Áp dụng từ 01/09.\nKỳ hạn 6 tháng: 5,2%/năm.");

    assertThat(flat).doesNotContain("#");
    assertThat(flat).doesNotContain(">");
    assertThat(flat).contains("Lãi suất tiền gửi");
    assertThat(flat).contains("Áp dụng từ 01/09.");
    assertThat(flat).contains("5,2%/năm");
  }

  @Test
  @DisplayName("Bold and italic still collapse, and a plain answer is untouched")
  void keepsExistingBehaviour() throws Exception {
    assertThat(strip("Phí là **50.000đ** mỗi năm")).isEqualTo("Phí là 50.000đ mỗi năm");
    assertThat(strip("Số dư: 1.000.000đ")).isEqualTo("Số dư: 1.000.000đ");
  }

  @Test
  @DisplayName("A dollar or backslash in a cell survives, rather than corrupting the replacement")
  void handlesRegexSpecialCharactersInCells() throws Exception {
    // Table cells are substituted into a regex replacement, where $ and \\ carry meaning. Real
    // answers contain both: currency and escaped punctuation.
    String flat = strip("| Phí | 100$ |\n| Ghi chú | a\\b |");

    assertThat(flat).contains("Phí · 100$");
    assertThat(flat).contains("Ghi chú · a\\b");
  }
}
