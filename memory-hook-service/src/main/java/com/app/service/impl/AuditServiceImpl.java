package com.app.service.impl;

import com.app.constant.AppConstants;
import com.app.dto.response.MemoryResponses.AuditRow;
import com.app.dto.response.MemoryResponses.AuditSummary;
import com.app.model.AccessAudit;
import com.app.repository.AccessAuditRepository;
import com.app.service.AuditService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditServiceImpl implements AuditService {

  private final AccessAuditRepository audits;

  public AuditServiceImpl(AccessAuditRepository audits) {
    this.audits = audits;
  }

  /**
   * A new transaction: the audit row must survive even when the read it describes is rolled back,
   * which is the whole point of an access log.
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String pseudoId, String actor, String scope, String operation, int records) {
    AccessAudit row = new AccessAudit();
    row.setPseudoId(pseudoId);
    row.setActor(actor);
    row.setScope(scope);
    row.setOperation(operation);
    row.setRecords(records);
    row.setRequestId(MDC.get(AppConstants.MDC_REQUEST_ID));
    audits.save(row);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AuditRow> search(String pseudoId, Instant from, Instant to, Pageable pageable) {
    Page<AccessAudit> page =
        pseudoId == null
            ? audits.findByAtBetweenOrderByAtDesc(from, to, pageable)
            : audits.findByPseudoIdAndAtBetweenOrderByAtDesc(pseudoId, from, to, pageable);
    return page.map(
        row ->
            new AuditRow(
                row.getId(),
                row.getPseudoId(),
                row.getActor(),
                row.getScope(),
                row.getOperation(),
                row.getRecords(),
                row.getRequestId(),
                row.getAt()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditSummary> summarise(String pseudoId) {
    Page<AccessAudit> page =
        audits.findByPseudoIdAndAtBetweenOrderByAtDesc(
            pseudoId,
            Instant.EPOCH,
            Instant.now(),
            Pageable.ofSize(AppConstants.PAGE_SIZE_MAX));
    Map<String, List<AccessAudit>> grouped =
        page.getContent().stream()
            .collect(Collectors.groupingBy(row -> row.getActor() + "|" + row.getOperation()));
    return grouped.values().stream()
        .map(
            rows -> {
              AccessAudit first = rows.get(0);
              Instant last =
                  rows.stream().map(AccessAudit::getAt).max(Comparator.naturalOrder()).orElse(null);
              return new AuditSummary(first.getActor(), first.getOperation(), rows.size(), last);
            })
        .sorted(Comparator.comparing(AuditSummary::actor))
        .toList();
  }
}
