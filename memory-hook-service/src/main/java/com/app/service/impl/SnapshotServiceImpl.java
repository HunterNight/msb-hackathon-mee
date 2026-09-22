package com.app.service.impl;

import com.app.constant.AppConstants;
import com.app.constant.ErrorCode;
import com.app.constant.MemoryConstants;
import com.app.dto.response.MemoryResponses.ExactDue;
import com.app.dto.response.MemoryResponses.GoalProgress;
import com.app.dto.response.MemoryResponses.NudgeHistory;
import com.app.dto.response.MemoryResponses.ProductCounts;
import com.app.dto.response.MemoryResponses.SnapshotPromptSlice;
import com.app.dto.response.MemoryResponses.SnapshotTriggerSlice;
import com.app.dto.response.MemoryResponses.UpcomingDue;
import com.app.exception.BusinessException;
import com.app.model.CustomerSnapshot;
import com.app.model.EventFact;
import com.app.model.MemoryRecord;
import com.app.repository.CustomerSnapshotRepository;
import com.app.repository.EventFactRepository;
import com.app.repository.MemoryRecordRepository;
import com.app.service.AuditService;
import com.app.service.ConsentService;
import com.app.service.SnapshotService;
import com.app.service.crypto.EnvelopeCipher;
import com.app.service.crypto.KeyService;
import com.app.service.privacy.Bucketizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Everything here is derived from {@code event_fact}, which already holds nothing but ids, buckets
 * and days. The prompt slice is stored in clear jsonb because it is safe by construction; the
 * exact slice is encrypted (design §3).
 */
@Service
public class SnapshotServiceImpl implements SnapshotService {

  private static final String OP_SNAPSHOT = "SNAPSHOT";
  private static final int SPEND_WINDOW_DAYS = 30;

  private final EventFactRepository facts;
  private final CustomerSnapshotRepository snapshots;
  private final MemoryRecordRepository records;
  private final ConsentService consentService;
  private final KeyService keyService;
  private final EnvelopeCipher cipher;
  private final Bucketizer bucketizer;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public SnapshotServiceImpl(
      EventFactRepository facts,
      CustomerSnapshotRepository snapshots,
      MemoryRecordRepository records,
      ConsentService consentService,
      KeyService keyService,
      EnvelopeCipher cipher,
      Bucketizer bucketizer,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.facts = facts;
    this.snapshots = snapshots;
    this.records = records;
    this.consentService = consentService;
    this.keyService = keyService;
    this.cipher = cipher;
    this.bucketizer = bucketizer;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public void rebuild(String pseudoId, byte[] dek) {
    if (!consentService.allowsSnapshot(pseudoId)) {
      snapshots.findById(pseudoId).ifPresent(snapshots::delete);
      return;
    }
    List<EventFact> window =
        facts.findByPseudoIdAndOccurredAtAfter(
            pseudoId, Instant.now().minus(MemoryConstants.SNAPSHOT_WINDOW));

    SnapshotTriggerSlice exact = computeExact(pseudoId, window);
    SnapshotPromptSlice bucketed = computeBucketed(pseudoId, window, exact);

    CustomerSnapshot snapshot =
        snapshots
            .findById(pseudoId)
            .orElseGet(
                () -> {
                  CustomerSnapshot fresh = new CustomerSnapshot();
                  fresh.setPseudoId(pseudoId);
                  return fresh;
                });
    snapshot.setSnapshotEnc(cipher.encrypt(dek, objectMapper.writeValueAsString(exact)));
    snapshot.setPublicSlice(objectMapper.writeValueAsString(bucketed));
    snapshot.setSnapshotVersion(snapshot.getSnapshotVersion() + 1);
    snapshot.setUpdatedAt(Instant.now());
    snapshots.save(snapshot);
  }

  @Override
  @Transactional(readOnly = true)
  public SnapshotPromptSlice promptSlice(UUID customerId, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    if (!consentService.allowsSnapshot(pseudoId)) {
      throw new BusinessException(ErrorCode.NO_CONSENT);
    }
    auditService.record(pseudoId, actor, "memory:read", OP_SNAPSHOT, 1);
    return promptSliceForExport(pseudoId);
  }

  @Override
  @Transactional(readOnly = true)
  public SnapshotPromptSlice promptSliceForExport(String pseudoId) {
    return snapshots
        .findById(pseudoId)
        .map(row -> objectMapper.readValue(row.getPublicSlice(), SnapshotPromptSlice.class))
        .orElseGet(this::emptyPrompt);
  }

  @Override
  @Transactional(readOnly = true)
  public SnapshotTriggerSlice triggerSlice(UUID customerId, String actor) {
    String pseudoId = keyService.pseudoId(customerId);
    if (!consentService.allowsSnapshot(pseudoId)) {
      throw new BusinessException(ErrorCode.NO_CONSENT);
    }
    Optional<byte[]> dek = keyService.dekIfPresent(pseudoId);
    Optional<CustomerSnapshot> snapshot = snapshots.findById(pseudoId);
    auditService.record(pseudoId, actor, "memory:read", OP_SNAPSHOT + "_TRIGGER", 1);
    if (dek.isEmpty() || snapshot.isEmpty()) {
      return emptyTrigger();
    }
    return objectMapper.readValue(
        cipher.decrypt(dek.get(), snapshot.get().getSnapshotEnc()), SnapshotTriggerSlice.class);
  }

  private SnapshotTriggerSlice computeExact(String pseudoId, List<EventFact> window) {
    Instant monthAgo = Instant.now().minus(SPEND_WINDOW_DAYS, ChronoUnit.DAYS);

    BigDecimal idleBalance =
        window.stream()
            .filter(fact -> MemoryConstants.FACT_BALANCE.equals(fact.getFactType()))
            .max(Comparator.comparing(EventFact::getOccurredAt))
            .map(EventFact::getAmount)
            .orElse(BigDecimal.ZERO);

    List<EventFact> spend =
        window.stream()
            .filter(fact -> MemoryConstants.FACT_SPEND.equals(fact.getFactType()))
            .toList();
    List<EventFact> monthSpendFacts =
        spend.stream().filter(fact -> fact.getOccurredAt().isAfter(monthAgo)).toList();
    BigDecimal monthSpend = sum(monthSpendFacts);

    Map<String, Integer> shares = new LinkedHashMap<>();
    Map<String, BigDecimal> byCategory =
        monthSpendFacts.stream()
            .collect(
                Collectors.groupingBy(
                    fact -> fact.getCategoryCode() == null ? "OTHER" : fact.getCategoryCode(),
                    Collectors.reducing(BigDecimal.ZERO, EventFact::getAmount, BigDecimal::add)));
    byCategory.forEach((category, amount) -> shares.put(category, bucketizer.sharePct(amount, monthSpend)));

    List<ExactDue> dues =
        window.stream()
            .filter(
                fact ->
                    List.of(
                            MemoryConstants.FACT_BILL,
                            MemoryConstants.FACT_CARD_DUE,
                            MemoryConstants.FACT_LOAN)
                        .contains(fact.getFactType()))
            .filter(fact -> fact.getOccurredAt().isAfter(monthAgo))
            .collect(Collectors.toMap(EventFact::getRefKey, Function.identity(), (a, b) -> b))
            .values()
            .stream()
            .map(
                fact ->
                    new ExactDue(
                        dueType(fact.getFactType()),
                        LocalDate.ofInstant(fact.getOccurredAt(), AppConstants.USER_ZONE),
                        fact.getAmount()))
            .sorted(Comparator.comparing(ExactDue::dueOn))
            .toList();

    List<GoalProgress> goals =
        window.stream()
            .filter(fact -> MemoryConstants.FACT_GOAL.equals(fact.getFactType()))
            .filter(fact -> fact.getPct() != null)
            .collect(Collectors.toMap(EventFact::getRefKey, Function.identity(), (a, b) -> b))
            .values()
            .stream()
            .map(fact -> new GoalProgress(fact.getRefKey(), fact.getPct(), behind(fact)))
            .toList();

    List<NudgeHistory> nudges =
        window.stream()
            .filter(fact -> MemoryConstants.FACT_NUDGE_DISMISSED.equals(fact.getFactType()))
            .sorted(Comparator.comparing(EventFact::getOccurredAt).reversed())
            .limit(10)
            .map(fact -> new NudgeHistory(fact.getRefKey(), fact.getOccurredAt(), true))
            .toList();

    return new SnapshotTriggerSlice(
        idleBalance, monthSpend, shares, dues, goals, nudges, Instant.now());
  }

  private SnapshotPromptSlice computeBucketed(
      String pseudoId, List<EventFact> window, SnapshotTriggerSlice exact) {

    List<EventFact> income =
        window.stream()
            .filter(fact -> MemoryConstants.FACT_INCOME.equals(fact.getFactType()))
            .toList();

    Integer payDay = null;
    if (income.size() >= MemoryConstants.SALARY_MIN_OCCURRENCES) {
      payDay =
          income.stream()
              .map(EventFact::getDayOfMonth)
              .filter(java.util.Objects::nonNull)
              .collect(Collectors.groupingBy(day -> day, Collectors.counting()))
              .entrySet()
              .stream()
              .max(Map.Entry.comparingByValue())
              .map(Map.Entry::getKey)
              .orElse(null);
    }

    String incomeBucket =
        income.stream()
            .max(Comparator.comparing(EventFact::getOccurredAt))
            .map(fact -> bucketizer.money(fact.getAmount()))
            .orElse(MemoryConstants.MONEY_BUCKETS.get(0));

    List<UpcomingDue> dues =
        exact.dues().stream()
            .map(
                due ->
                    new UpcomingDue(
                        due.type(), due.dueOn().getDayOfMonth(), bucketizer.money(due.amount())))
            .toList();

    ProductCounts products =
        new ProductCounts(
            distinct(window, MemoryConstants.FACT_DEPOSIT),
            distinct(window, MemoryConstants.FACT_GOAL),
            distinct(window, MemoryConstants.FACT_CARD_DUE),
            distinct(window, MemoryConstants.FACT_LOAN));

    // Preferences are already the abstract text of stored records, so they are prompt-safe.
    List<String> preferences =
        records
            .findLive(
                pseudoId,
                MemoryConstants.KIND_PREFERENCE,
                Instant.now(),
                org.springframework.data.domain.PageRequest.of(0, 10))
            .map(MemoryRecord::getAbstractText)
            .getContent();

    return new SnapshotPromptSlice(
        payDay,
        incomeBucket,
        bucketizer.money(exact.idleBalance()),
        exact.categoryShares(),
        spendDelta(window),
        dues,
        products,
        preferences,
        Instant.now());
  }

  /** This month's spend against the previous month's, as a signed percentage. */
  private int spendDelta(List<EventFact> window) {
    Instant now = Instant.now();
    Instant monthAgo = now.minus(SPEND_WINDOW_DAYS, ChronoUnit.DAYS);
    Instant twoMonthsAgo = now.minus(2L * SPEND_WINDOW_DAYS, ChronoUnit.DAYS);
    List<EventFact> spend =
        window.stream()
            .filter(fact -> MemoryConstants.FACT_SPEND.equals(fact.getFactType()))
            .toList();
    BigDecimal current =
        sum(spend.stream().filter(fact -> fact.getOccurredAt().isAfter(monthAgo)).toList());
    BigDecimal previous =
        sum(
            spend.stream()
                .filter(
                    fact ->
                        fact.getOccurredAt().isAfter(twoMonthsAgo)
                            && !fact.getOccurredAt().isAfter(monthAgo))
                .toList());
    if (previous.signum() == 0) {
      return 0;
    }
    return current
        .subtract(previous)
        .multiply(BigDecimal.valueOf(100))
        .divide(previous, 0, java.math.RoundingMode.HALF_UP)
        .intValue();
  }

  private int behind(EventFact goal) {
    // A goal is "behind" by however far its progress trails the share of the year elapsed.
    int elapsed = LocalDate.now(AppConstants.USER_ZONE).getDayOfYear() * 100 / 365;
    return Math.max(0, elapsed - goal.getPct());
  }

  private int distinct(List<EventFact> window, String factType) {
    return (int)
        window.stream()
            .filter(fact -> factType.equals(fact.getFactType()))
            .map(EventFact::getRefKey)
            .distinct()
            .count();
  }

  private BigDecimal sum(List<EventFact> facts) {
    return facts.stream()
        .map(EventFact::getAmount)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private String dueType(String factType) {
    return switch (factType) {
      case MemoryConstants.FACT_CARD_DUE -> "CARD";
      case MemoryConstants.FACT_LOAN -> "LOAN";
      default -> "BILL";
    };
  }

  private SnapshotPromptSlice emptyPrompt() {
    return new SnapshotPromptSlice(
        null,
        MemoryConstants.MONEY_BUCKETS.get(0),
        MemoryConstants.MONEY_BUCKETS.get(0),
        Map.of(),
        0,
        List.of(),
        new ProductCounts(0, 0, 0, 0),
        List.of(),
        Instant.now());
  }

  private SnapshotTriggerSlice emptyTrigger() {
    return new SnapshotTriggerSlice(
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        Map.of(),
        new ArrayList<>(),
        List.of(),
        List.of(),
        Instant.now());
  }
}
