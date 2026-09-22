package com.app.service.impl;

import com.app.constant.MemoryConstants;
import com.app.service.EmbeddingService;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * A deterministic hashed bag-of-words embedding, used when no model gateway is configured. It is
 * good enough for the recall demo and — more importantly — it keeps the abstract text inside the
 * service, which is exactly what the privacy design asks for on the local profile (design §4).
 */
@Service
@ConditionalOnProperty(name = "app.memory.embedding.provider", havingValue = "local",
    matchIfMissing = true)
public class HashingEmbeddingService implements EmbeddingService {

  private static final int NGRAM = 3;

  @Override
  public float[] embed(String abstractText) {
    float[] vector = new float[MemoryConstants.EMBEDDING_DIM];
    String normalised = fold(abstractText);
    for (String token : normalised.split("\\s+")) {
      if (token.isBlank()) {
        continue;
      }
      add(vector, token);
      // Character n-grams keep Vietnamese morphology and typos close together.
      for (int i = 0; i + NGRAM <= token.length(); i++) {
        add(vector, token.substring(i, i + NGRAM));
      }
    }
    return normalise(vector);
  }

  private void add(float[] vector, String token) {
    int hash = token.hashCode();
    int index = Math.floorMod(hash, vector.length);
    vector[index] += (hash & 1) == 0 ? 1f : -1f;
  }

  private float[] normalise(float[] vector) {
    double sum = 0;
    for (float v : vector) {
      sum += (double) v * v;
    }
    double norm = Math.sqrt(sum);
    if (norm == 0) {
      return vector;
    }
    for (int i = 0; i < vector.length; i++) {
      vector[i] = (float) (vector[i] / norm);
    }
    return vector;
  }

  private String fold(String text) {
    String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
    String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
    return new String(
            decomposed.replaceAll("\\p{M}", "").replaceAll("[^a-z0-9 ]", " ").getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8)
        .trim();
  }
}
