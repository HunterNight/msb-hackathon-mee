package com.app.service;

import com.app.service.MemoryRecordService.Candidate;
import com.app.service.extract.ExtractionContext;
import java.util.List;

/**
 * Turns a masked transcript into candidate records. The model sees redacted, pseudonymised text
 * wrapped as {@code <untrusted source="events">} and its output is schema-validated before
 * anything is stored (design §4).
 */
public interface SummarizerService {

  List<Candidate> summarise(ExtractionContext context, List<String> maskedTurns);
}
