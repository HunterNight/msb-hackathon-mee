package com.app.service.extract;

import com.app.constant.MemoryConstants;
import com.app.service.MemoryRecordService.Candidate;
import com.app.service.client.MiTranscriptClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * "anh Minh" → a beneficiary id, and only when Mi confirmed the resolution in the conversation.
 * The alias is stored; the account number behind the beneficiary never is (design §3).
 */
@Component
public class NicknameExtractor implements MemoryExtractor {

  private final MiTranscriptClient transcripts;

  public NicknameExtractor(MiTranscriptClient transcripts) {
    this.transcripts = transcripts;
  }

  @Override
  public boolean supports(String eventType) {
    return MemoryConstants.EVENT_CONVERSATION_CLOSED.equals(eventType);
  }

  @Override
  public List<Candidate> extract(ExtractionContext context) {
    String conversationId = context.string("conversationId");
    if (conversationId == null) {
      return List.of();
    }
    List<Candidate> candidates = new ArrayList<>();
    for (MiTranscriptClient.ResolvedAlias alias : transcripts.aliases(conversationId)) {
      if (!alias.confirmed()) {
        // An unconfirmed guess is exactly the kind of thing memory poisoning relies on.
        continue;
      }
      candidates.add(
          new Candidate(
              context.pseudoId(),
              context.dek(),
              MemoryConstants.KIND_NICKNAME,
              "Gọi người nhận này là \"" + alias.alias() + "\"",
              MemoryConstants.KIND_NICKNAME + ":" + alias.beneficiaryId(),
              new BigDecimal("0.850"),
              MemoryConstants.SOURCE_SUMMARY,
              Map.of("beneficiaryId", alias.beneficiaryId()),
              context.eventId(),
              context.eventType(),
              context.eventTime()));
    }
    return candidates;
  }
}
