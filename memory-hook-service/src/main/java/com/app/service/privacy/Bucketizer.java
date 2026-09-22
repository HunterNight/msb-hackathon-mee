package com.app.service.privacy;

import com.app.constant.MemoryConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Money becomes a bucket and dates become a day-of-month before anything is written to the public
 * slice, so a prompt can say "10M-20M" but never the balance (design §4 "Minimisation").
 */
@Component
public class Bucketizer {

  public String money(BigDecimal amount) {
    if (amount == null) {
      return MemoryConstants.MONEY_BUCKETS.get(0);
    }
    BigDecimal value = amount.abs();
    for (int i = 0; i < MemoryConstants.MONEY_BUCKET_CEILINGS.size(); i++) {
      if (value.compareTo(MemoryConstants.MONEY_BUCKET_CEILINGS.get(i)) < 0) {
        return MemoryConstants.MONEY_BUCKETS.get(i);
      }
    }
    return MemoryConstants.MONEY_BUCKETS.get(MemoryConstants.MONEY_BUCKETS.size() - 1);
  }

  /** Percentage share, rounded to whole points — enough for a prompt, too coarse to fingerprint. */
  public int sharePct(BigDecimal part, BigDecimal total) {
    if (total == null || total.signum() == 0) {
      return 0;
    }
    return part.multiply(BigDecimal.valueOf(100))
        .divide(total, 0, RoundingMode.HALF_UP)
        .intValue();
  }
}
