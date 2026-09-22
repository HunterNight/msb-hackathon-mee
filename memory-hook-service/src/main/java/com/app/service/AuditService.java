package com.app.service;

import com.app.dto.response.MemoryResponses.AuditRow;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Every read of customer memory leaves a row; ops can only ever see the pseudonym (design §4). */
public interface AuditService {

  void record(String pseudoId, String actor, String scope, String operation, int records);

  Page<AuditRow> search(String pseudoId, Instant from, Instant to, Pageable pageable);

  List<com.app.dto.response.MemoryResponses.AuditSummary> summarise(String pseudoId);
}
