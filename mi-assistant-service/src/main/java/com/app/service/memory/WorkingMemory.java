package com.app.service.memory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The 30-minute Redis window Mi keeps for a live conversation. Long-term memory belongs to
 * memory-hook-service and is never written from here (design §M3.9).
 */
public interface WorkingMemory {

  void remember(UUID conversationId, String role, String text);

  List<String> recent(UUID conversationId, int limit);

  void clear(UUID conversationId);

  /**
   * The screen the customer came from, kept for the conversation so a follow-up turn still resolves
   * "this loan" after the app stops sending the entry context (06 §6, M3.12).
   */
  void rememberScreen(UUID conversationId, String screen, String entityType, UUID entityId);

  Optional<ScreenMemory> screen(UUID conversationId);

  record ScreenMemory(String screen, String entityType, UUID entityId) {}
}
