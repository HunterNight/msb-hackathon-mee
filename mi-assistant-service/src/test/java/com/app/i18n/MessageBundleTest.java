package com.app.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Every user-facing string is en/vi (guideline README "Non-negotiables"). A missing key is silent
 * at runtime — the fallback is the key itself — so it is caught here instead.
 */
class MessageBundleTest {

  private static Properties load(String locale) throws IOException {
    Properties properties = new Properties();
    try (var stream =
        MessageBundleTest.class.getResourceAsStream("/i18n/messages_" + locale + ".properties")) {
      assertThat(stream).as("messages_%s.properties is on the classpath", locale).isNotNull();
      properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    return properties;
  }

  @Test
  @DisplayName("Vietnamese and English define exactly the same keys")
  void bundlesHaveIdenticalKeys() throws IOException {
    Set<String> vi = new TreeSet<>(load("vi").stringPropertyNames());
    Set<String> en = new TreeSet<>(load("en").stringPropertyNames());

    Set<String> onlyVi = new TreeSet<>(vi);
    onlyVi.removeAll(en);
    Set<String> onlyEn = new TreeSet<>(en);
    onlyEn.removeAll(vi);

    assertThat(onlyVi).as("keys defined only in Vietnamese").isEmpty();
    assertThat(onlyEn).as("keys defined only in English").isEmpty();
  }

  @Test
  @DisplayName("No key is left with an empty translation")
  void noBlankTranslations() throws IOException {
    for (String locale : List.of("vi", "en")) {
      Properties properties = load(locale);
      for (String key : properties.stringPropertyNames()) {
        assertThat(properties.getProperty(key).trim())
            .as("%s is blank in messages_%s.properties", key, locale)
            .isNotEmpty();
      }
    }
  }

  /** The strings this upgrade introduced, which the card and loan agents resolve at runtime. */
  static Stream<String> newKeys() {
    return Stream.of(
        "mi.card.title.cardIssue",
        "mi.card.row.approvedLimit",
        "mi.card.row.annualFee",
        "mi.card.row.perk",
        "mi.card.row.physical",
        "mi.card.value.yes",
        "mi.card.value.no",
        "mi.card.unlocked",
        "mi.card.issue.footnote",
        "mi.log.card_issue.title",
        "mi.error.issueNotAllowed",
        "mi.error.propertyDataUnavailable",
        "mi.error.estimateNotForApproval",
        "mi.narrate.mortgage.line",
        "mi.narrate.mortgage.range",
        "mi.narrate.mortgage.cap.LTV",
        "mi.narrate.mortgage.cap.PRODUCT_MAX",
        "mi.narrate.mortgage.cap.PRE_APPROVAL",
        "mi.narrate.mortgage.cap.INCOME_DTI",
        "mi.narrate.mortgage.estimateNote",
        "mi.narrate.affordability.line",
        "mi.narrate.affordability.down",
        "mi.narrate.cardProducts.lead",
        "mi.narrate.cardProducts.line",
        "mi.narrate.cardEligibility.yes",
        "mi.narrate.cardEligibility.no",
        "mi.narrate.unlock.none");
  }

  @ParameterizedTest(name = "{0} resolves in both locales")
  @MethodSource("newKeys")
  @DisplayName("The keys added by the card-lifecycle and mortgage work resolve to real text")
  void newKeysResolve(String key) {
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("i18n/messages");
    messages.setDefaultEncoding(StandardCharsets.UTF_8.name());

    for (Locale locale : List.of(Locale.forLanguageTag("vi"), Locale.ENGLISH)) {
      String resolved = messages.getMessage(key, new Object[] {"1", "2", "3", "4", "5"}, locale);
      // The fallback for a missing key is the key itself, which is what this guards against.
      assertThat(resolved).as("%s in %s", key, locale).isNotEqualTo(key).isNotBlank();
    }
  }

  /**
   * A cap reason travels from lending-service as a bare string and is turned into a message key by
   * concatenation, so every value the service can emit needs a matching key.
   */
  @Test
  @DisplayName("Every mortgage cap reason lending-service can emit has narration in both locales")
  void everyCapReasonHasNarration() throws IOException {
    Properties vi = load("vi");
    Properties en = load("en");

    for (String reason : List.of("LTV", "PRODUCT_MAX", "PRE_APPROVAL", "INCOME_DTI")) {
      String key = "mi.narrate.mortgage.cap." + reason;
      assertThat(vi.getProperty(key)).as("%s in vi", key).isNotNull();
      assertThat(en.getProperty(key)).as("%s in en", key).isNotNull();
    }
  }
}
