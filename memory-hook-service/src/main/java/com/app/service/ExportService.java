package com.app.service;

import com.app.dto.response.MemoryResponses.ExportResponse;
import java.util.UUID;

/** Data-subject export: the decrypted exact text, behind a short-lived signed URL. */
public interface ExportService {

  ExportResponse export(UUID customerId, String actor);

  /** Serves a previously issued export; the token is single-use and expires in five minutes. */
  String fetch(String token);
}
