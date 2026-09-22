package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.service.tool.AmountParser;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** Small talk is answered by a rule, with no documents and no model — "xin chào" used to get "chưa có thông tin". */
class RuleModelGatewaySmallTalkTest {

  private static final Locale VI = Locale.forLanguageTag("vi");

  private final RuleModelGateway gateway = new RuleModelGateway(new AmountParser(), messages());

  private static ResourceBundleMessageSource messages() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }

  @Test
  @DisplayName("Greetings get a greeting that says what MEE can do")
  void greeting() {
    for (String hello : new String[] {"Xin chào", "chào bạn", "Hello", "hi", "Chào MEE"}) {
      var reply = gateway.smallTalk(hello, VI);
      assertThat(reply).as(hello).isPresent();
      assertThat(reply.get().text()).as(hello).contains("MEE có thể giúp");
    }
  }

  @Test
  @DisplayName("A banking question that happens to contain 'hi' or 'chào' is not small talk")
  void notEveryMessageIsAGreeting() {
    assertThat(gateway.smallTalk("Chào bạn, cho tôi hỏi lãi suất tiết kiệm 12 tháng là bao nhiêu", VI)).isEmpty();
    assertThat(gateway.smallTalk("Tôi muốn chuyển tiền", VI)).isEmpty();
  }

  @Test
  @DisplayName("Thanks and 'what can you do' still work")
  void existingRules() {
    assertThat(gateway.smallTalk("Cảm ơn bạn nhé", VI)).isPresent();
    assertThat(gateway.smallTalk("Bạn có thể làm gì cho tôi?", VI)).isPresent();
  }
}
