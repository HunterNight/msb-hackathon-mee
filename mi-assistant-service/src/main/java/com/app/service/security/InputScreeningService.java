package com.app.service.security;

import com.app.dto.internal.MiInternal.ScreeningResult;
import java.util.UUID;

/** The gate every user turn and every retrieved chunk passes through (design §12.2). */
public interface InputScreeningService {

  ScreeningResult screen(UUID customerId, String text);

  /** Rejects a chunk that carries instructions aimed at the assistant. */
  boolean chunkIsSafe(String content);

  /** Records a refused turn and returns true once the fraud-review threshold is crossed. */
  boolean flag(UUID customerId, String label, String excerpt);
}
