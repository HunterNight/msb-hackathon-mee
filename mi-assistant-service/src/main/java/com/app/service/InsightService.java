package com.app.service;

import com.app.dto.response.MiResponses.InsightDto;
import java.util.Locale;
import java.util.UUID;

/** The MiInsightBanner text for one placement (design §M2.7). */
public interface InsightService {

  /** Null when nothing is relevant, or when the customer turned home suggestions off. */
  InsightDto forPlacement(UUID customerId, String placement, Locale locale);
}
