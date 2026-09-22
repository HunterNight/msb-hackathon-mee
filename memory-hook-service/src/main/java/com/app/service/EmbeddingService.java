package com.app.service;

/** Turns an abstract sentence into the vector stored beside the record. */
public interface EmbeddingService {

  float[] embed(String abstractText);
}
