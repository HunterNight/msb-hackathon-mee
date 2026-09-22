package com.app.service.impl;

import com.app.constant.MiConstants;
import com.app.dto.response.MiResponses.MemoryConsentDto;
import com.app.dto.response.MiResponses.MemoryRecordDto;
import com.app.dto.response.MiResponses.MemoryResponse;
import com.app.service.client.PeerClients.MemoryClient;
import com.app.service.memory.MemoryService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Every call here is best-effort: memory is an enrichment, and a hook outage must degrade the
 * answer, never fail the turn.
 */
@Service
public class MemoryServiceImpl implements MemoryService {

  private static final Logger log = LoggerFactory.getLogger(MemoryServiceImpl.class);

  private final MemoryClient memory;

  public MemoryServiceImpl(MemoryClient memory) {
    this.memory = memory;
  }

  @Override
  public List<String> recallForPrompt(UUID customerId, String question) {
    return recallForPrompt(customerId, question, null);
  }

  @Override
  public List<String> recallForPrompt(UUID customerId, String question, String intent) {
    try {
      List<String> allowedKinds =
          intent == null ? null : MiConstants.MEMORY_KINDS_BY_INTENT.getOrDefault(intent, List.of());
      if (allowedKinds != null && allowedKinds.isEmpty()) {
        return List.of();
      }
      String kindsParam = allowedKinds == null ? null : String.join(",", allowedKinds);
      var response =
          memory.recall(customerId, question, MiConstants.RAG_TOP_K, kindsParam).data();
      if (response == null || !response.consent().longTerm()) {
        return List.of();
      }
      return java.util.stream.Stream.concat(
              response.pinned().stream().map(MemoryClient.RecordDto::text),
              response.matches().stream().map(match -> match.record().text()))
          .distinct()
          .toList();
    } catch (Exception e) {
      log.debug("memory recall unavailable for this turn");
      return List.of();
    }
  }

  @Override
  public MemoryResponse list(UUID customerId) {
    try {
      boolean longTerm = consent(customerId);
      List<MemoryRecordDto> records =
          memory.records(customerId).data().stream()
              .map(record -> toRecordDto(record))
              .toList();
      return new MemoryResponse(new MemoryConsentDto(longTerm), records);
    } catch (Exception e) {
      return new MemoryResponse(new MemoryConsentDto(false), List.of());
    }
  }

  private MemoryRecordDto toRecordDto(MemoryClient.RecordDto record) {
    return new MemoryRecordDto(
        record.id(),
        record.kind(),
        record.text(),
        record.confidence(),
        record.pinned(),
        record.createdAt(),
        "CHAT".equals(record.source()) ? "CHAT" : "EXTRACTOR",
        record.state(),
        record.explicit(),
        record.customerConfirmed(),
        record.lastConfirmedAt(),
        record.persistence(),
        record.entity(),
        record.evidenceSummary());
  }

  @Override
  public boolean delete(UUID customerId, UUID recordId) {
    try {
      return memory.deleteRecord(customerId, recordId).data().deleted();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public int deleteAll(UUID customerId) {
    try {
      int count = memory.records(customerId).data().size();
      memory.erase(
          customerId, new MemoryClient.ErasureRequestBody("CUSTOMER_REQUEST", "mi"));
      return count;
    } catch (Exception e) {
      return 0;
    }
  }

  @Override
  public boolean consent(UUID customerId) {
    try {
      var response = memory.consent(customerId).data();
      return response != null && response.longTerm();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean setConsent(UUID customerId, boolean longTerm) {
    try {
      var response =
          memory
              .updateConsent(
                  customerId,
                  new MemoryClient.ConsentRequest(
                      longTerm, null, null, MiConstants.POLICY_VERSION, "MI_SETTINGS"))
              .data();
      return response != null && response.longTerm();
    } catch (Exception e) {
      log.warn("could not update memory consent for the customer");
      return longTerm;
    }
  }

  @Override
  public Map<String, Object> promptSnapshot(UUID customerId) {
    try {
      Map<String, Object> slice =
          memory.snapshot(customerId, MiConstants.SLICE_PROMPT_NAME, null).data();
      return slice == null ? Map.of() : slice;
    } catch (Exception e) {
      return Map.of();
    }
  }

  @Override
  public Map<String, Object> triggerSnapshot(UUID customerId) {
    try {
      Map<String, Object> slice =
          memory
              .snapshot(customerId, MiConstants.SLICE_TRIGGER_NAME, MiConstants.SLICE_TRIGGER_NAME)
              .data();
      return slice == null ? Map.of() : slice;
    } catch (Exception e) {
      return Map.of();
    }
  }

  @Override
  public com.app.dto.response.MiResponses.CandidateDecisionDto submitCandidate(
      UUID customerId, com.app.dto.response.MiResponses.CandidateRequestDto request) {
    try {
      var response =
          memory
              .submitCandidate(
                  customerId,
                  new MemoryClient.CandidateRequest(
                      request.kind(), request.text(), request.entity(), request.reason(),
                      request.confidence(), request.refs(),
                      request.conversationId(), request.messageId()))
              .data();
      return new com.app.dto.response.MiResponses.CandidateDecisionDto(
          response.decision(),
          response.record() == null ? null : toRecordDto(response.record()));
    } catch (Exception e) {
      log.debug("memory candidate submit unavailable");
      return new com.app.dto.response.MiResponses.CandidateDecisionDto("ERROR", null);
    }
  }

  @Override
  public com.app.dto.response.MiResponses.MemoryRecordDto confirm(UUID customerId, UUID recordId) {
    try {
      return toRecordDto(memory.confirm(customerId, recordId).data());
    } catch (Exception e) {
      log.debug("memory confirm unavailable");
      throw new RuntimeException(e);
    }
  }

  @Override
  public com.app.dto.response.MiResponses.MemoryRecordDto reject(UUID customerId, UUID recordId) {
    try {
      return toRecordDto(memory.reject(customerId, recordId).data());
    } catch (Exception e) {
      log.debug("memory reject unavailable");
      throw new RuntimeException(e);
    }
  }

  @Override
  public com.app.dto.response.MiResponses.MemoryRecordDto edit(
      UUID customerId, UUID recordId, com.app.dto.response.MiResponses.EditMemoryRequest request) {
    try {
      return toRecordDto(
          memory
              .editRecord(
                  customerId, recordId, new MemoryClient.EditRequest(request.text(), request.reason()))
              .data());
    } catch (Exception e) {
      log.debug("memory edit unavailable");
      throw new RuntimeException(e);
    }
  }

  @Override
  public com.app.dto.response.MiResponses.MemoryRecordDto deactivate(UUID customerId, UUID recordId) {
    try {
      return toRecordDto(memory.deactivate(customerId, recordId).data());
    } catch (Exception e) {
      log.debug("memory deactivate unavailable");
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean forget(UUID customerId, UUID recordId) {
    try {
      return memory.forget(customerId, recordId).data().deleted();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public com.app.dto.response.MiResponses.MemoryRecordDto nextHypothesis(UUID customerId) {
    try {
      var record = memory.hypotheses(customerId).data();
      return record == null ? null : toRecordDto(record);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public com.app.dto.response.MiResponses.MemoryGraphDto graph(UUID customerId) {
    try {
      var response = memory.graph(customerId).data();
      if (response == null) {
        return new com.app.dto.response.MiResponses.MemoryGraphDto(List.of(), List.of());
      }
      List<com.app.dto.response.MiResponses.MemoryRecordDto> nodes =
          response.nodes().stream().map(this::toRecordDto).toList();
      List<com.app.dto.response.MiResponses.MemoryLinkDto> links =
          response.links().stream()
              .map(
                  l ->
                      new com.app.dto.response.MiResponses.MemoryLinkDto(
                          l.id(), l.fromRecord(), l.toRecord(), l.relation(),
                          l.state(), l.createdAt(), l.confirmedAt()))
              .toList();
      return new com.app.dto.response.MiResponses.MemoryGraphDto(nodes, links);
    } catch (Exception e) {
      return new com.app.dto.response.MiResponses.MemoryGraphDto(List.of(), List.of());
    }
  }

  @Override
  public com.app.dto.response.MiResponses.MemoryWhyDto why(UUID customerId, UUID recordId) {
    try {
      var response = memory.why(customerId, recordId).data();
      return response == null
          ? null
          : new com.app.dto.response.MiResponses.MemoryWhyDto(
              response.recordId(), response.kind(), response.entity(),
              response.evidenceSummary(), response.reason(), response.createdAt());
    } catch (Exception e) {
      return null;
    }
  }
}
