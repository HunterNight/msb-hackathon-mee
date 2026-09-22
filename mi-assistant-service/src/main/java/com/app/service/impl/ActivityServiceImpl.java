package com.app.service.impl;

import com.app.constant.AppConstants;
import com.app.constant.MiConstants;
import com.app.dto.request.MiRequests.LogActivityRequest;
import com.app.dto.response.MiResponses.ActivityGroupDto;
import com.app.dto.response.MiResponses.ActivityItemDto;
import com.app.model.ActivityLog;
import com.app.repository.ActivityLogRepository;
import com.app.service.ActivityService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ActivityServiceImpl implements ActivityService {

  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

  private final ActivityLogRepository logs;
  private final MessageSource messages;
  private final ObjectMapper objectMapper;

  public ActivityServiceImpl(
      ActivityLogRepository logs, MessageSource messages, ObjectMapper objectMapper) {
    this.logs = logs;
    this.messages = messages;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public UUID log(LogActivityRequest request) {
    ActivityLog row = new ActivityLog();
    row.setCustomerId(request.customerId());
    row.setKind(request.kind());
    row.setTitleKey(request.titleKey());
    row.setTitleArgs(json(request.titleArgs()));
    row.setSubtitleKey(request.subtitleKey());
    row.setSubtitleArgs(json(request.subtitleArgs()));
    row.setProposalId(request.proposalId());
    row.setRef(request.ref());
    row.setDeepLink(request.deepLink());
    row.setOccurredAt(request.occurredAt() == null ? Instant.now() : request.occurredAt());
    return logs.save(row).getId();
  }

  @Override
  @Transactional
  public void log(
      UUID customerId,
      String kind,
      String titleKey,
      Map<String, Object> titleArgs,
      String subtitleKey,
      Map<String, Object> subtitleArgs,
      UUID proposalId,
      String ref,
      String deepLink) {
    log(
        new LogActivityRequest(
            customerId, kind, titleKey, titleArgs, subtitleKey, subtitleArgs, ref, proposalId,
            deepLink, Instant.now()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActivityGroupDto> feed(
      UUID customerId, String filter, Pageable pageable, Locale locale) {

    List<String> kinds =
        switch (filter == null ? "ALL" : filter) {
          case "AUTO" -> List.of(MiConstants.LOG_AUTO);
          case "CONFIRM" -> List.of(MiConstants.LOG_CONFIRM);
          // BLOCKED_SAFETY is deliberately absent: refusals are audited, not shown (design §12.4).
          default ->
              List.of(
                  MiConstants.LOG_AUTO,
                  MiConstants.LOG_CONFIRM,
                  MiConstants.LOG_BLOCKED,
                  MiConstants.LOG_INSIGHT);
        };

    Map<LocalDate, List<ActivityItemDto>> grouped = new LinkedHashMap<>();
    logs.findByCustomerIdAndKindInOrderByOccurredAtDesc(customerId, kinds, pageable)
        .forEach(
            row -> {
              LocalDate date = LocalDate.ofInstant(row.getOccurredAt(), AppConstants.USER_ZONE);
              grouped.computeIfAbsent(date, key -> new ArrayList<>()).add(toItem(row, locale));
            });

    List<ActivityGroupDto> groups = new ArrayList<>();
    grouped.forEach((date, items) -> groups.add(new ActivityGroupDto(dayLabel(date, locale), date,
        items)));
    return groups;
  }

  private ActivityItemDto toItem(ActivityLog row, Locale locale) {
    return new ActivityItemDto(
        row.getId(),
        row.getKind(),
        message(row.getTitleKey(), row.getTitleArgs(), locale),
        message(row.getSubtitleKey(), row.getSubtitleArgs(), locale),
        message("mi.log.tag." + row.getKind(), "{}", locale),
        icon(row.getKind()),
        tone(row.getKind()),
        row.getOccurredAt(),
        row.getProposalId(),
        row.getRef(),
        row.getDeepLink());
  }

  /** "Hôm nay" / "Hôm qua" / "02/09" — the design's day headers. */
  private String dayLabel(LocalDate date, Locale locale) {
    LocalDate today = LocalDate.now(AppConstants.USER_ZONE);
    if (date.equals(today)) {
      return messages.getMessage("mi.log.today", null, locale);
    }
    if (date.equals(today.minusDays(1))) {
      return messages.getMessage("mi.log.yesterday", null, locale);
    }
    return date.format(DAY);
  }

  private String icon(String kind) {
    return switch (kind) {
      case MiConstants.LOG_AUTO -> "bolt";
      case MiConstants.LOG_CONFIRM -> "check";
      case MiConstants.LOG_BLOCKED -> "shield";
      default -> "sparkle";
    };
  }

  private String tone(String kind) {
    return switch (kind) {
      case MiConstants.LOG_BLOCKED -> "DESTRUCTIVE";
      case MiConstants.LOG_CONFIRM -> "POSITIVE";
      default -> "ACCENT";
    };
  }

  private String message(String key, String argsJson, Locale locale) {
    Map<String, Object> args = read(argsJson);
    // Arguments are positional in the bundle: {0}, {1}, … keyed "0", "1", … by the producer.
    Object[] ordered =
        args.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .toArray();
    return messages.getMessage(key, ordered, key, locale);
  }

  private Map<String, Object> read(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  private String json(Map<String, Object> args) {
    return objectMapper.writeValueAsString(args == null ? Map.of() : args);
  }
}
