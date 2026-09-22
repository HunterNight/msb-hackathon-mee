package com.app.service.client;

import java.util.List;

/**
 * The only call this service makes into mi-assistant, and it asks for the masked transcript
 * (scope {@code mi:transcript}) — never the raw one (design §2 H4).
 */
public interface MiTranscriptClient {

  List<String> maskedTranscript(String conversationId);

  /** Aliases Mi resolved to a beneficiary during the conversation, with the confirmation flag. */
  List<ResolvedAlias> aliases(String conversationId);

  record ResolvedAlias(String alias, String beneficiaryId, boolean confirmed) {}
}
