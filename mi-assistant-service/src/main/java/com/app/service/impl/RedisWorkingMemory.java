package com.app.service.impl;

import com.app.constant.MiConstants;
import com.app.service.memory.WorkingMemory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisWorkingMemory implements WorkingMemory {

  private static final String KEY = "mi:wm:";
  /** Separate key from the turn list, so the screen survives the window being trimmed. */
  private static final String SCREEN_KEY = "mi:wm:screen:";
  private static final String FIELD_SCREEN = "screen";
  private static final String FIELD_ENTITY_TYPE = "entityType";
  private static final String FIELD_ENTITY_ID = "entityId";

  private final StringRedisTemplate redis;

  public RedisWorkingMemory(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void remember(UUID conversationId, String role, String text) {
    String key = KEY + conversationId;
    redis.opsForList().rightPush(key, role + ": " + text);
    redis.opsForList().trim(key, -MiConstants.CHAT_HISTORY_WINDOW, -1);
    redis.expire(key, MiConstants.WORKING_MEMORY_TTL);
  }

  @Override
  public List<String> recent(UUID conversationId, int limit) {
    List<String> turns = redis.opsForList().range(KEY + conversationId, -limit, -1);
    return turns == null ? List.of() : turns;
  }

  @Override
  public void clear(UUID conversationId) {
    redis.delete(KEY + conversationId);
    redis.delete(SCREEN_KEY + conversationId);
  }

  @Override
  public void rememberScreen(
      UUID conversationId, String screen, String entityType, UUID entityId) {

    if (screen == null) {
      return;
    }
    String key = SCREEN_KEY + conversationId;
    // Replaced wholesale rather than patched: moving to a screen with no entity must clear the
    // previous entity, or "this card" would still resolve after the customer navigated away.
    redis.delete(key);
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(FIELD_SCREEN, screen);
    if (entityType != null) {
      fields.put(FIELD_ENTITY_TYPE, entityType);
    }
    if (entityId != null) {
      fields.put(FIELD_ENTITY_ID, entityId.toString());
    }
    redis.opsForHash().putAll(key, fields);
    redis.expire(key, MiConstants.WORKING_MEMORY_TTL);
  }

  @Override
  public Optional<ScreenMemory> screen(UUID conversationId) {
    Map<Object, Object> stored = redis.opsForHash().entries(SCREEN_KEY + conversationId);
    if (stored == null || stored.isEmpty()) {
      return Optional.empty();
    }
    Object screen = stored.get(FIELD_SCREEN);
    if (screen == null) {
      return Optional.empty();
    }
    Object entityType = stored.get(FIELD_ENTITY_TYPE);
    Object entityId = stored.get(FIELD_ENTITY_ID);
    UUID parsedId = null;
    if (entityId != null) {
      try {
        parsedId = UUID.fromString(entityId.toString());
      } catch (IllegalArgumentException ignored) {
        // A malformed id is simply no id; the tool then asks which entity.
        parsedId = null;
      }
    }
    return Optional.of(
        new ScreenMemory(
            screen.toString(), entityType == null ? null : entityType.toString(), parsedId));
  }
}
