package com.app.service.rag;

import com.app.dto.internal.MiInternal.RetrievedChunk;
import com.app.dto.response.MiResponses.CitationDto;
import java.util.List;

/**
 * The model receives chunks tagged {@code [doc:{id}]}; this strips the tags out of the reply and
 * turns the ones it actually used into citations (design §3.1).
 */
public interface CitationPostProcessor {

  Result process(String reply, List<RetrievedChunk> chunks);

  /** True when the reply quotes a rate or an amount with nothing to back it up (§3.1 guardrail). */
  boolean hasUncitedNumbers(String reply, Result result, boolean toolBacked);

  record Result(String text, List<CitationDto> citations) {}
}
