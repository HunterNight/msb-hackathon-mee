package com.app.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Mi has two different ways of not answering, and they must not share one message.
 *
 * <p>"I didn't understand you" ({@code mi.reply.clarify}) belongs to a turn Mi could not interpret.
 * A knowledge search that ran across every collection and matched nothing is a gap in what Mi knows
 * — the customer was clear. Serving the first message for the second case blames the customer and,
 * at Level 3, offers general-agent actions to someone who asked a domain agent a domain question.
 */
class NoAnswerCopyTest {

  private static final List<Locale> LOCALES =
      List.of(Locale.forLanguageTag("vi"), Locale.ENGLISH);

  private ResourceBundleMessageSource messages() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("i18n/messages");
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    return source;
  }

  @Test
  @DisplayName("The two failures have distinct copy in both locales")
  void clarifyAndNoAnswerAreDifferent() {
    for (Locale locale : LOCALES) {
      String clarify = messages().getMessage("mi.reply.clarify", null, locale);
      String noAnswer = messages().getMessage("mi.reply.noAnswer", null, locale);

      assertThat(noAnswer).as("noAnswer in %s", locale).isNotBlank().isNotEqualTo(clarify);
    }
  }

  @ParameterizedTest(name = "the {0} agent has its own no-answer copy")
  @ValueSource(strings = {"loan", "card", "saving", "payment"})
  @DisplayName("Each domain agent answers on-topic rather than falling back to general copy")
  void perAgentCopyExistsAndIsOnTopic(String agentCode) {
    for (Locale locale : LOCALES) {
      String generic = messages().getMessage("mi.reply.noAnswer", null, locale);
      String forAgent =
          messages().getMessage("mi.reply.noAnswer." + agentCode, null, generic, locale);

      // Falling through to the generic text is the bug this guards: the loan agent telling a
      // customer to try a transfer or a deposit is what made the hand-off look broken.
      assertThat(forAgent)
          .as("mi.reply.noAnswer.%s in %s", agentCode, locale)
          .isNotBlank()
          .isNotEqualTo(generic);
    }
  }

  @ParameterizedTest(name = "{0} offers a way forward")
  @ValueSource(
      strings = {
        "mi.reply.noAnswer",
        "mi.reply.noAnswer.loan",
        "mi.reply.noAnswer.card",
        "mi.reply.noAnswer.saving",
        "mi.reply.noAnswer.payment"
      })
  @DisplayName("A no-answer reply is never a dead end: it routes the customer somewhere")
  void everyNoAnswerOffersAnEscalation(String key) {
    for (Locale locale : LOCALES) {
      assertThat(messages().getMessage(key, null, locale))
          .as("%s in %s should name the support line", key, locale)
          .contains("1900 6083");
    }
  }

  @Test
  @DisplayName("No-answer copy carries no bare rate or amount that would trip the citation guard")
  void copyCannotTripTheUncitedNumberGuard() {
    // GuardrailServiceImpl.NUMBER matches a digit run followed by %, ₫, đ or VND. The support
    // number must not be written in a way that looks like an uncited figure.
    java.util.regex.Pattern number =
        java.util.regex.Pattern.compile("\\d[\\d.,]*\\s*(%|₫|đ\\b|VND)");

    for (String key :
        List.of(
            "mi.reply.noAnswer",
            "mi.reply.noAnswer.loan",
            "mi.reply.noAnswer.card",
            "mi.reply.noAnswer.saving",
            "mi.reply.noAnswer.payment")) {
      for (Locale locale : LOCALES) {
        String copy = messages().getMessage(key, null, locale);
        assertThat(number.matcher(copy).find())
            .as("%s in %s must not look like an uncited number", key, locale)
            .isFalse();
      }
    }
  }
}
