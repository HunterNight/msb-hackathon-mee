package com.app.service.impl;

import com.app.constant.MiConstants;
import com.app.dto.internal.MiInternal.RetrievedChunk;
import com.app.dto.response.MiResponses.CitationDto;
import com.app.service.rag.CitationPostProcessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CitationPostProcessorImpl implements CitationPostProcessor {

  private static final Pattern TAG = Pattern.compile("\\[doc:([0-9a-fA-F-]{36})]");
  /** A percentage or a đồng amount — the two things the model must never invent. */
  private static final Pattern NUMBER = Pattern.compile("\\d[\\d.,]*\\s*(%|₫|đ\\b|VND)");

  @Override
  public Result process(String reply, List<RetrievedChunk> chunks) {
    if (reply == null) {
      return new Result("", List.of());
    }
    Map<UUID, String> titles = new LinkedHashMap<>();
    for (RetrievedChunk chunk : chunks) {
      titles.putIfAbsent(chunk.docId(), chunk.title());
    }

    List<CitationDto> citations = new ArrayList<>();
    Matcher matcher = TAG.matcher(reply);
    while (matcher.find() && citations.size() < MiConstants.CITATION_MAX) {
      UUID docId = UUID.fromString(matcher.group(1));
      String title = titles.get(docId);
      if (title != null && citations.stream().noneMatch(c -> c.docId().equals(docId))) {
        citations.add(new CitationDto(docId, title));
      }
    }
    return new Result(TAG.matcher(reply).replaceAll("").replaceAll("\\s{2,}", " ").strip(),
        citations);
  }

  @Override
  public boolean hasUncitedNumbers(String reply, Result result, boolean toolBacked) {
    if (toolBacked || !result.citations().isEmpty()) {
      return false;
    }
    return NUMBER.matcher(reply == null ? "" : reply).find();
  }
}
