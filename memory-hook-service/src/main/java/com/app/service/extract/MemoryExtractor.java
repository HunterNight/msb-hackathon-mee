package com.app.service.extract;

import com.app.service.MemoryRecordService.Candidate;
import java.util.List;

/**
 * Rules first: an extractor only produces a candidate when the evidence in {@code event_fact}
 * already justifies it. The LLM is used for phrasing, never for deciding (design §5).
 */
public interface MemoryExtractor {

  boolean supports(String eventType);

  List<Candidate> extract(ExtractionContext context);
}
