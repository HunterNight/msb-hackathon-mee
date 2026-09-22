package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.constant.AgentCodes;
import com.app.constant.MiConstants;
import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.Guardrails;
import com.app.dto.internal.MiInternal.LocalizedText;
import com.app.dto.internal.MiInternal.ModelReply;
import com.app.dto.internal.MiInternal.ToolSpec;
import com.app.service.model.ModelGateway.Turn;
import com.app.service.tool.AmountParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * A transfer takes a bank, an account number and an amount, and customers give them over several
 * messages. The details are kept in the conversation's working memory ("USER: …" / "MI: …" lines), MEE
 * asks only for what is still missing, and nothing is proposed until the draft is complete. Before
 * this, "Tôi muốn chuyển tiền" fell through to the knowledge base and got the steps for doing it
 * oneself.
 *
 * <p>Uses the real message bundle, so the replies are the ones a customer reads and the way a later
 * turn recognises MEE's own question is exercised for real.
 */
class RuleModelGatewayMissingDetailsTest {

  private static final Locale VI = Locale.forLanguageTag("vi");
  private static final Locale EN = Locale.ENGLISH;

  private final ResourceBundleMessageSource messages = bundle();
  private final RuleModelGateway gateway = new RuleModelGateway(new AmountParser(), messages);
  private final GuardrailServiceImpl guardrails = new GuardrailServiceImpl();

  private static ResourceBundleMessageSource bundle() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }

  private static ToolSpec tool(String code) {
    return new ToolSpec(
        code, new LocalizedText(code, code), code, null, code, Map.of(), false, null, true,
        List.of());
  }

  private static final List<ToolSpec> TOOLS =
      List.of(tool(ToolCodes.PROPOSE_TRANSFER), tool(ToolCodes.PROPOSE_DEPOSIT));

  /** One conversation: what working memory holds, turn by turn, exactly as ChatServiceImpl writes it. */
  private final class Chat {
    private final List<String> memory = new ArrayList<>();
    private final Locale locale;

    Chat(Locale locale) {
      this.locale = locale;
    }

    ModelReply say(String text) {
      memory.add(MiConstants.ROLE_USER + ": " + text);
      ModelReply reply =
          gateway.chat(
              null, "S", memory.stream().map(line -> new Turn("history", line)).toList(), text,
              TOOLS, locale);
      if (reply.text() != null) {
        // What the customer is shown, and what is remembered, has been through the guardrail.
        memory.add(MiConstants.ROLE_MI + ": " + shown(reply.text()));
      } else if (ToolCodes.PROPOSE_TRANSFER.equals(reply.toolCode())) {
        memory.add(MiConstants.ROLE_MI + ": " + messages.getMessage("mi.reply.proposalReady", null, locale));
      }
      return reply;
    }
  }

  private String shown(String reply) {
    return guardrails.checkReply(agent(), reply, false, false);
  }

  private static AgentSpec agent() {
    return new AgentSpec(
        AgentCodes.GENERAL,
        new LocalizedText("Mi", "Mi"),
        AgentCodes.DOMAIN_GENERAL,
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

  private static BigDecimal vnd(String amount) {
    return new BigDecimal(amount);
  }

  // ── the opening: nothing known yet ─────────────────────────────────────────

  @Test
  @DisplayName("'Tôi muốn chuyển tiền' asks for the bank, the account number and the amount")
  void openingAsksForEverything() {
    ModelReply reply = new Chat(VI).say("Tôi muốn chuyển tiền");
    assertThat(reply.toolCode()).isNull();
    assertThat(reply.text())
        .contains("ngân hàng nhận")
        .contains("số tài khoản nhận")
        .contains("số tiền");
  }

  // ── collecting over several messages ───────────────────────────────────────

  @Test
  @DisplayName("Bank and account first, then the amount, then a proposal")
  void collectsBankAccountThenAmount() {
    Chat chat = new Chat(VI);
    chat.say("Tôi muốn chuyển tiền");

    ModelReply second = chat.say("mab 0123455");
    assertThat(second.toolCode()).isNull();
    assertThat(second.text()).contains("MSB").contains("0123455").contains("số tiền chuyển");

    ModelReply third = chat.say("6 triệu");
    assertThat(third.toolCode()).isEqualTo(ToolCodes.PROPOSE_TRANSFER);
    assertThat(third.toolArgs())
        .containsEntry("amount", vnd("6000000"))
        .containsEntry("bankCode", "MSB")
        .containsEntry("accountNumber", "0123455")
        .doesNotContainKey("recipientQuery");
  }

  @Test
  @DisplayName("The amount first, then the bank and account")
  void collectsAmountFirst() {
    Chat chat = new Chat(VI);
    chat.say("Tôi muốn chuyển tiền");

    ModelReply second = chat.say("500k");
    assertThat(second.toolCode()).isNull();
    assertThat(second.text()).contains("500.000").contains("ngân hàng nhận").contains("số tài khoản nhận");

    ModelReply third = chat.say("vcb 0123456789");
    assertThat(third.toolCode()).isEqualTo(ToolCodes.PROPOSE_TRANSFER);
    assertThat(third.toolArgs())
        .containsEntry("amount", vnd("500000"))
        .containsEntry("bankCode", "VCB")
        .containsEntry("accountNumber", "0123456789");
  }

  @Test
  @DisplayName("Each message can carry more than one detail, and only the missing ones are asked")
  void everythingInTwoMessages() {
    Chat chat = new Chat(VI);
    chat.say("Tôi muốn chuyển tiền");
    ModelReply reply = chat.say("6 triệu cho techcombank 19035678901234");
    assertThat(reply.toolCode()).isEqualTo(ToolCodes.PROPOSE_TRANSFER);
    assertThat(reply.toolArgs())
        .containsEntry("bankCode", "TCB")
        .containsEntry("amount", vnd("6000000"));
  }

  @Test
  @DisplayName("A later message corrects an earlier one")
  void laterMessageWins() {
    Chat chat = new Chat(VI);
    chat.say("Tôi muốn chuyển tiền");
    chat.say("5 triệu cho vcb 0123456789");
    // proposed already, so this is a fresh draft: a correction only applies inside one exchange
    Chat other = new Chat(VI);
    other.say("Tôi muốn chuyển tiền");
    other.say("5 triệu");
    ModelReply reply = other.say("à nhầm, 3 triệu cho vcb 0123456789");
    assertThat(reply.toolArgs()).containsEntry("amount", vnd("3000000"));
  }

  @Test
  @DisplayName("A saved recipient by name only needs an amount")
  void savedRecipient() {
    Chat chat = new Chat(VI);
    chat.say("Tôi muốn chuyển tiền");
    ModelReply second = chat.say("cho mẹ");
    assertThat(second.toolCode()).isNull();
    assertThat(second.text()).contains("người nhận mẹ").contains("số tiền chuyển");

    ModelReply third = chat.say("500k");
    assertThat(third.toolCode()).isEqualTo(ToolCodes.PROPOSE_TRANSFER);
    assertThat(third.toolArgs())
        .containsEntry("amount", vnd("500000"))
        .containsEntry("recipientQuery", "mẹ")
        .doesNotContainKey("bankCode");
  }

  @Test
  @DisplayName("A one-off account with an unknown bank asks for the bank, keeping the rest")
  void unknownBank() {
    Chat chat = new Chat(VI);
    ModelReply reply = chat.say("chuyển 6 triệu cho xyz 0123456789");
    assertThat(reply.toolCode()).isNull();
    assertThat(reply.text()).contains("0123456789").contains("6.000.000").contains("ngân hàng nhận");
  }

  @Test
  @DisplayName("The account number is never read as the amount")
  void accountNumberIsNotTheAmount() {
    ModelReply reply = new Chat(VI).say("chuyển cho vietcombank 0123456789");
    assertThat(reply.toolCode()).isNull();
    assertThat(reply.text()).contains("VCB").contains("0123456789").contains("số tiền chuyển");
    assertThat(reply.text()).doesNotContain("123.456.789");
  }

  // ── complete in one message ────────────────────────────────────────────────

  @Test
  @DisplayName("'chuyển 6 triệu cho mab 0123455' proposes at once")
  void completeMessageProposes() {
    ModelReply reply = new Chat(VI).say("chuyển 6 triệu cho mab 0123455");
    assertThat(reply.toolCode()).isEqualTo(ToolCodes.PROPOSE_TRANSFER);
    assertThat(reply.toolArgs())
        .containsEntry("amount", vnd("6000000"))
        .containsEntry("bankCode", "MSB")
        .containsEntry("accountNumber", "0123455")
        .doesNotContainKey("recipientQuery");
  }

  @Test
  @DisplayName("Bank names resolve to the transfer-service code")
  void bankNames() {
    assertThat(new Chat(VI).say("Chuyển 500k cho vcb 0123456789").toolArgs())
        .containsEntry("bankCode", "VCB");
    assertThat(new Chat(VI).say("chuyển 2 triệu vào tài khoản techcombank 19035678901234").toolArgs())
        .containsEntry("bankCode", "TCB")
        .containsEntry("accountNumber", "19035678901234");
    assertThat(new Chat(VI).say("chuyển 1tr5 cho 0123456789 agribank").toolArgs())
        .containsEntry("bankCode", "AGR")
        .containsEntry("amount", vnd("1500000"));
  }

  @Test
  @DisplayName("A plain eight-digit amount before 'cho' is an amount, not an account")
  void bigPlainAmount() {
    ModelReply reply = new Chat(VI).say("chuyển 10000000 cho anh Minh");
    assertThat(reply.toolArgs())
        .containsEntry("amount", vnd("10000000"))
        .containsEntry("recipientQuery", "anh Minh")
        .doesNotContainKey("accountNumber");
  }

  // ── leaving the exchange ───────────────────────────────────────────────────

  @Test
  @DisplayName("Cancelling ends the exchange")
  void cancel() {
    Chat chat = new Chat(VI);
    chat.say("Tôi muốn chuyển tiền");
    ModelReply reply = chat.say("thôi hủy đi");
    assertThat(reply.toolCode()).isNull();
    assertThat(reply.text()).contains("Đã huỷ");
  }

  @Test
  @DisplayName("Once a proposal is made, a later message is not treated as more transfer details")
  void proposalEndsTheExchange() {
    Chat chat = new Chat(VI);
    chat.say("chuyển 6 triệu cho mab 0123455");
    ModelReply next = chat.say("500k");
    assertThat(next.toolCode()).isNotEqualTo(ToolCodes.PROPOSE_TRANSFER);
  }

  @Test
  @DisplayName("Questions about transferring are not answered with a request for details")
  void productQuestionsAreLeftAlone() {
    for (String question :
        List.of(
            "Phí chuyển tiền là bao nhiêu?",
            "Hạn mức chuyển tiền tối đa",
            "Chuyển tiền như thế nào?",
            "Hướng dẫn chuyển tiền")) {
      ModelReply reply = new Chat(VI).say(question);
      assertThat(reply.text() == null ? "" : reply.text()).as(question).doesNotContain("ngân hàng nhận");
      assertThat(reply.toolCode()).as(question).isNotEqualTo(ToolCodes.PROPOSE_TRANSFER);
    }
  }

  // ── the guardrail and English ──────────────────────────────────────────────

  @Test
  @DisplayName("MEE's own questions pass the guardrail untouched, so they can be recognised next turn")
  void questionsSurviveTheGuardrail() {
    for (Locale locale : List.of(VI, EN)) {
      Chat chat = new Chat(locale);
      for (String turn :
          List.of("I want to transfer money", "vcb 0123456789", "6 triệu", "cho mẹ")) {
        ModelReply reply = chat.say(turn);
        if (reply.text() != null) {
          assertThat(shown(reply.text())).as("%s / %s", locale, turn).isEqualTo(reply.text());
        }
      }
    }
  }

  @Test
  @DisplayName("The same exchange works in English")
  void english() {
    Chat chat = new Chat(EN);
    ModelReply first = chat.say("I want to send money");
    assertThat(first.text()).contains("receiving bank");
    ModelReply second = chat.say("VCB 0123456789");
    assertThat(second.text()).contains("VCB").contains("0123456789").contains("the amount");
    ModelReply third = chat.say("2000000");
    assertThat(third.toolCode()).isEqualTo(ToolCodes.PROPOSE_TRANSFER);
    assertThat(third.toolArgs()).containsEntry("amount", vnd("2000000"));
  }

  // ── saving: the same "ask, don't instruct" rule ───────────────────────────

  @Test
  @DisplayName("Opening a saving with no amount asks the amount and term; a complete one proposes")
  void deposit() {
    assertThat(new Chat(VI).say("Tôi muốn mở sổ tiết kiệm").text())
        .contains("Bạn muốn gửi tiết kiệm bao nhiêu tiền");
    assertThat(new Chat(VI).say("Mở sổ tiết kiệm 10 triệu 6 tháng").toolCode())
        .isEqualTo(ToolCodes.PROPOSE_DEPOSIT);
    ModelReply rate = new Chat(VI).say("Lãi suất mở sổ tiết kiệm bao nhiêu?");
    assertThat(rate.text() == null ? "" : rate.text()).doesNotContain("Bạn muốn gửi tiết kiệm");
  }
}
