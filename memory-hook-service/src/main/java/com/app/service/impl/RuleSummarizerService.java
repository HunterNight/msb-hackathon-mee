package com.app.service.impl;

import com.app.constant.MemoryConstants;
import com.app.service.MemoryRecordService.Candidate;
import com.app.service.SummarizerService;
import com.app.service.extract.ExtractionContext;
import com.app.service.privacy.DlpValidator;
import com.app.service.privacy.PiiRedactor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The rules-only summariser: it looks for the handful of statements that are worth remembering and
 * phrases them itself. It is the default because it needs no model gateway, and because a rule can
 * be audited — which matters more here than fluency (design §5 "rules first").
 *
 * <p>Every turn is treated as untrusted input: text is redacted, sensitive statements are dropped,
 * and an instruction hidden in a message can only ever become a low-confidence FACT that the DLP
 * gate then has to accept.
 */
@Service
public class RuleSummarizerService implements SummarizerService {

  private static final Map<String, String> GOAL_MARKERS =
      Map.of(
          "muốn tiết kiệm", "Có ý định tiết kiệm thêm",
          "muốn mua", "Có kế hoạch mua sắm lớn",
          "du lịch", "Có kế hoạch đi du lịch",
          "mua nhà", "Có kế hoạch mua nhà",
          "mua xe", "Có kế hoạch mua xe");

  private static final Map<String, String> PREFERENCE_MARKERS =
      Map.of(
          "không thích", "Không muốn nhận gợi ý tương tự",
          "dùng pin", "Thích xác thực bằng PIN",
          "sinh trắc", "Thích xác thực bằng sinh trắc học",
          "tiếng anh", "Thích trao đổi bằng tiếng Anh");

  private final PiiRedactor redactor;
  private final DlpValidator dlp;

  public RuleSummarizerService(PiiRedactor redactor, DlpValidator dlp) {
    this.redactor = redactor;
    this.dlp = dlp;
  }

  @Override
  public List<Candidate> summarise(ExtractionContext context, List<String> maskedTurns) {
    List<Candidate> candidates = new ArrayList<>();
    for (String turn : maskedTurns) {
      String safe = redactor.redact(turn).toLowerCase(Locale.ROOT);
      collect(context, candidates, safe, GOAL_MARKERS, MemoryConstants.KIND_GOAL, "0.750");
      collect(
          context, candidates, safe, PREFERENCE_MARKERS, MemoryConstants.KIND_PREFERENCE, "0.720");
    }
    return candidates;
  }

  private void collect(
      ExtractionContext context,
      List<Candidate> candidates,
      String turn,
      Map<String, String> markers,
      String kind,
      String confidence) {

    markers.forEach(
        (marker, phrasing) -> {
          if (!turn.contains(marker) || !dlp.acceptable(kind, phrasing)) {
            return;
          }
          candidates.add(
              new Candidate(
                  context.pseudoId(),
                  context.dek(),
                  kind,
                  phrasing,
                  kind + ":" + marker.replace(' ', '-'),
                  new BigDecimal(confidence),
                  MemoryConstants.SOURCE_SUMMARY,
                  Map.of(),
                  context.eventId(),
                  context.eventType(),
                  context.eventTime()));
        });
  }
}
