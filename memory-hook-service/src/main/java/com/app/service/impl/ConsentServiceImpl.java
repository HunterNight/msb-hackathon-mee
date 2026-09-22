package com.app.service.impl;

import com.app.constant.MemoryConstants;
import com.app.dto.request.MemoryRequests.ConsentRequest;
import com.app.dto.response.MemoryResponses.ConsentResponse;
import com.app.model.MemoryConsent;
import com.app.repository.CustomerSnapshotRepository;
import com.app.repository.MemoryConsentRepository;
import com.app.service.AuditService;
import com.app.service.ConsentService;
import com.app.service.ErasureService;
import com.app.service.crypto.KeyService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentServiceImpl implements ConsentService {

  private static final String OP_READ = "CONSENT_READ";
  private static final String OP_WRITE = "CONSENT_WRITE";

  private final MemoryConsentRepository consents;
  private final CustomerSnapshotRepository snapshots;
  private final KeyService keyService;
  private final AuditService auditService;
  private final ErasureService erasureService;

  public ConsentServiceImpl(
      MemoryConsentRepository consents,
      CustomerSnapshotRepository snapshots,
      KeyService keyService,
      AuditService auditService,
      @Lazy ErasureService erasureService) {
    this.consents = consents;
    this.snapshots = snapshots;
    this.keyService = keyService;
    this.auditService = auditService;
    this.erasureService = erasureService;
  }

  /**
   * A customer we have never seen has the default posture: aggregates yes, long-term memory yes. The
   * row is only written once the customer actually decides something, so switching the toggle off in Mi
   * settings persists an explicit opt-out that this default never overrides. What is remembered still
   * needs the customer's confirmation before it is recalled (guideline 11 §4).
   */
  @Override
  @Transactional(readOnly = true)
  public MemoryConsent forPseudo(String pseudoId) {
    return consents.findById(pseudoId).orElseGet(() -> defaults(pseudoId));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean allowsRecords(String pseudoId) {
    return forPseudo(pseudoId).isLongTerm();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean allowsSnapshot(String pseudoId) {
    return forPseudo(pseudoId).isSnapshot();
  }

  @Override
  @Transactional(readOnly = true)
  public ConsentResponse get(UUID customerId) {
    String pseudoId = keyService.pseudoId(customerId);
    auditService.record(pseudoId, actor(), "memory:read", OP_READ, 1);
    return toDto(forPseudo(pseudoId));
  }

  @Override
  @Transactional
  public ConsentResponse update(UUID customerId, ConsentRequest request, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    // Touch the key row so the consent row has its foreign key even on a first-ever decision.
    keyService.dek(customerId);

    MemoryConsent consent = consents.findById(pseudoId).orElseGet(() -> defaults(pseudoId));
    boolean wasLongTerm = consent.isLongTerm();
    boolean wasSnapshot = consent.isSnapshot();

    if (request.longTerm() != null) {
      consent.setLongTerm(request.longTerm());
    }
    if (request.snapshot() != null) {
      consent.setSnapshot(request.snapshot());
    }
    if (request.improveModels() != null) {
      consent.setImproveModels(request.improveModels());
    }
    consent.setPolicyVersion(request.policyVersion());
    consent.setSource(request.source());
    consent.setUpdatedAt(Instant.now());
    MemoryConsent saved = consents.save(consent);

    // Withdrawal is not just a flag: the data it covered has to go.
    if (wasLongTerm && !saved.isLongTerm()) {
      erasureService.queueForConsentWithdrawal(pseudoId, actor);
    }
    if (wasSnapshot && !saved.isSnapshot()) {
      snapshots.deleteById(pseudoId);
    }
    auditService.record(pseudoId, actor, "memory:erase", OP_WRITE, 1);
    return toDto(saved);
  }

  private MemoryConsent defaults(String pseudoId) {
    MemoryConsent consent = new MemoryConsent();
    consent.setPseudoId(pseudoId);
    consent.setLongTerm(true);
    consent.setSnapshot(true);
    consent.setImproveModels(false);
    consent.setPolicyVersion(MemoryConstants.POLICY_VERSION_DEFAULT);
    consent.setSource("ONBOARDING");
    return consent;
  }

  private ConsentResponse toDto(MemoryConsent consent) {
    return new ConsentResponse(
        consent.isLongTerm(),
        consent.isSnapshot(),
        consent.isImproveModels(),
        consent.getPolicyVersion(),
        consent.getUpdatedAt() == null ? consent.getCreatedAt() : consent.getUpdatedAt());
  }

  private String actor() {
    var authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    return authentication == null ? "system" : authentication.getName();
  }
}
