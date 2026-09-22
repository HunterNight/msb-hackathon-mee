package com.app.service.rag;

/** Turns text into the vector stored on {@code document_chunk} (design §3.1). */
public interface EmbeddingService {

  float[] embed(String text);
}
