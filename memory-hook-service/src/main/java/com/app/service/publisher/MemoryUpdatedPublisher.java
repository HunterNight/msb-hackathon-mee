package com.app.service.publisher;

import com.app.constant.MemoryConstants;
import java.util.List;

/**
 * The compacted {@code msb.memory.updated} topic, keyed by pseudo id. Mi invalidates its recall
 * cache from it; a null value is the erasure tombstone (design §5).
 */
public interface MemoryUpdatedPublisher {

  void published(String pseudoId, List<String> kinds, int version);

  /** @return the offset of the tombstone, which goes into the erasure proof */
  long tombstone(String pseudoId);

  default String topic() {
    return MemoryConstants.TOPIC_MEMORY_UPDATED;
  }
}
