package com.app.service.rag;

import com.app.dto.internal.MiInternal.RetrievedChunk;
import java.util.List;
import java.util.Locale;

/** Collection-scoped similarity search with the confidence threshold (design §3.1). */
public interface RetrievalService {

  List<RetrievedChunk> search(List<String> collectionCodes, String question, Locale locale, int topK);

  /** True when nothing cleared the threshold, which is the clarification path. */
  boolean belowThreshold(List<RetrievedChunk> chunks);
}
