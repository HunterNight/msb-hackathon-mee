package com.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.constant.AgentCodes;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.Guardrails;
import com.app.dto.internal.MiInternal.LocalizedText;
import com.app.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * BRD §5.3a creepiness control. The surveillance phrasings are only forbidden on turns that used
 * customer memory — elsewhere they are ordinary banking information, and blocking them on every
 * reply stopped Mi saying when a loan or a card statement is due.
 */
class GuardrailSurveillanceTest {

  private final GuardrailServiceImpl guardrails = new GuardrailServiceImpl();

  private static AgentSpec agent() {
    return new AgentSpec(
        AgentCodes.GENERAL,
        new LocalizedText("MEE", "MEE"),
        AgentCodes.DOMAIN_GENERAL,
        true,
        1,
        new LocalizedText("Bạn là MEE.", "You are MEE."),
        null,
        BigDecimal.valueOf(0.2),
        List.of(),
        List.of(),
        new Guardrails(List.of(), "k", null, true, 4, false),
        List.of(),
        null,
        "test");
  }

  @ParameterizedTest
  @DisplayName("Surveillance phrasing is rejected when the turn used memory")
  @ValueSource(
      strings = {
        "MEE biết bạn thường đến Golf Club vào thứ Bảy.",
        "Bạn chơi Golf lúc 7 giờ sáng mỗi cuối tuần.",
        "Bạn đã có 5 giao dịch tại sân Golf tháng này.",
        "Bạn hay đi Golf vào chủ nhật.",
      })
  void rejectsSurveillanceWithMemory(String reply) {
    assertThatThrownBy(() -> guardrails.checkReply(agent(), reply, true, true))
        .isInstanceOf(BusinessException.class);
  }

  @ParameterizedTest
  @DisplayName("The same banking facts pass when no memory was used")
  @ValueSource(
      strings = {
        "Khoản vay trả vào ngày 25 hàng tháng, tự động trừ từ tài khoản chính.",
        "Sao kê thẻ đến hạn vào ngày 15, bạn nên thanh toán trước để tránh phí.",
        "Bạn có thể trả góp trong 12 lần với lãi suất 0%.",
        "Bạn đã sai mã PIN 3 lần, thẻ tạm khoá trong 24 giờ.",
        "Mỗi ngày bạn được chuyển tối đa 20 giao dịch qua Mi.",
      })
  void allowsBankingFactsWithoutMemory(String reply) {
    assertThat(guardrails.checkReply(agent(), reply, true, false)).isNotBlank();
  }

  @ParameterizedTest
  @DisplayName("Weekdays are whole words — 'thứ tự', 'thứ nhất' are not surveillance")
  @ValueSource(
      strings = {
        "Làm theo thứ tự: mở ứng dụng, chọn Thẻ, bấm Khoá.",
        "Đây là lựa chọn thứ nhất phù hợp với mục tiêu của bạn.",
        "MEE nhận thấy bạn có vẻ quan tâm đến Golf. MEE đoán đúng không?",
      })
  void allowsNonWeekdayThuEvenWithMemory(String reply) {
    assertThat(guardrails.checkReply(agent(), reply, true, true)).isNotBlank();
  }
}
