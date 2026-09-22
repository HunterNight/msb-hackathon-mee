package com.app.service.impl;

import com.app.constant.MiConstants;
import com.app.service.rag.EmbeddingService;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Deterministic hashed embedding, used when no GreenNode embedding model is configured. The
 * design explicitly allows a local 384-dimension fallback; keeping it in-process also means the
 * demo runs with no external key at all.
 */
@Service
@ConditionalOnProperty(name = "app.mi.embedding.provider", havingValue = "local",
    matchIfMissing = true)
public class HashingEmbeddingService implements EmbeddingService {

  private static final int NGRAM = 3;

  @Override
  public float[] embed(String text) {
    float[] vector = new float[MiConstants.EMBEDDING_DIM];
    for (String token : fold(text).split("\\s+")) {
      if (token.isBlank()) {
        continue;
      }
      add(vector, token);
      for (int i = 0; i + NGRAM <= token.length(); i++) {
        add(vector, token.substring(i, i + NGRAM));
      }
    }
    return normalise(vector);
  }

  private void add(float[] vector, String token) {
    int hash = token.hashCode();
    vector[Math.floorMod(hash, vector.length)] += (hash & 1) == 0 ? 1f : -1f;
  }

  private float[] normalise(float[] vector) {
    double sum = 0;
    for (float value : vector) {
      sum += (double) value * value;
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

  /** Diacritic-folded so "khoá thẻ" and "khoa the" land in the same neighbourhood. */
  private String fold(String text) {
    String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
    return Normalizer.normalize(lower, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replace('đ', 'd')
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }
}
