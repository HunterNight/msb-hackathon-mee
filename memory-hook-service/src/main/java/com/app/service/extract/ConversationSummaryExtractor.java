package com.app.service.extract;

import com.app.constant.MemoryConstants;
import com.app.service.MemoryRecordService.Candidate;
import com.app.service.SummarizerService;
import com.app.service.client.MiTranscriptClient;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The transcript is fetched masked and summarised into candidate records. The transcript text
 * itself is never stored, and the summariser's output is schema-validated before it gets here
 * (design §4 "LLM boundary").
 */
@Component
public class ConversationSummaryExtractor implements MemoryExtractor {

  private final MiTranscriptClient transcripts;
  private final SummarizerService summarizer;

  public ConversationSummaryExtractor(
      MiTranscriptClient transcripts, SummarizerService summarizer) {
    this.transcripts = transcripts;
    this.summarizer = summarizer;
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
    List<String> turns = transcripts.maskedTranscript(conversationId);
    if (turns.isEmpty()) {
      return List.of();
    }
    return summarizer.summarise(context, turns);
  }
}
