package com.app.service.model;

import com.app.dto.internal.MiInternal.RetrievedChunk;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Hands the prose of a knowledge answer to the VRM agent ({@code services/vrm-agent}).
 *
 * <p>The turn stays mi-assistant's: it screens the input, routes, retrieves, runs tools, applies
 * guardrails, persists and streams. Only the wording of a grounded answer is delegated, because that
 * is the one part the agent does better — mi's Level 1 answer is the best matching passage truncated
 * at 600 characters, which is accurate and reads like a document excerpt.
 *
 * <p>Two rules keep the delegation safe, and both are enforced by the caller rather than here:
 *
 * <ul>
 *   <li><b>Never when a tool ran.</b> Tool results are narrated deterministically in Java. The agent
 *       is wired to mock tools and, asked about a customer's cards, listed two card numbers that were
 *       not theirs — so it must never be the voice describing someone's money.
 *   <li><b>Never without grounding.</b> The passages mi retrieved are sent as the authority and the
 *       agent is told they outrank its own corpus. Asked the six-month deposit rate on its own it
 *       answers 4,7%/năm while the bank's published document says 5,2%/năm. Ungrounded, it invents;
 *       grounded, it paraphrases what the bank actually published.
 * </ul>
 */
public interface VrmComposer {

  /** Whether delegation is configured at all; false makes every turn behave as it did before. */
  boolean enabled();

  /**
   * The agent's wording of an answer to {@code question}, grounded in {@code chunks}.
   *
   * @return empty whenever the agent is unreachable, slow, errors, or answers with nothing — the
   *     caller then composes the answer itself, so a failure here can never cost the customer their
   *     reply.
   */
  Optional<String> compose(
      String agentCode,
      String question,
      List<RetrievedChunk> chunks,
      UUID customerId,
      UUID conversationId,
      Locale locale);
}
