package com.app.service.impl;

import com.app.constant.ErrorCode;
import com.app.constant.MiConstants;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.Guardrails;
import com.app.exception.BusinessException;
import com.app.service.agent.GuardrailService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class GuardrailServiceImpl implements GuardrailService {

  private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|msb://)\\S+");
  private static final Pattern NUMBER = Pattern.compile("\\d[\\d.,]*\\s*(%|₫|đ\\b|VND)");
  /** Anything that would mean the system prompt leaked into the answer. */
  private static final Pattern PROMPT_LEAK =
      Pattern.compile("(?i)(<untrusted|system prompt|bạn là mi, trợ lý|you are mi,|bạn là mee,|you are mee,)");

  /**
   * Surveillance-style facts patterns (BRD §5.3a creepiness control): a reply that mentions a
   * weekday, a time of day, or an exact count of the customer's behaviour is forbidden in code,
   * not only in the prompt.
   *
   * <p>Applied only on turns that used customer memory. The same phrasings are ordinary banking
   * information everywhere else — "trả vào ngày 25", "trả góp 12 lần", "sai mã PIN 3 lần",
   * "thứ tự" — and blocking them on every reply stopped Mi saying when a loan or statement is due.
   * Weekdays are whole words: a character class here matched "thứ tự" and "thứ nhất".
   */
  private static final Pattern SURVEILLANCE =
      Pattern.compile(
          "(?iu)(thứ\\s*(hai|ba|tư|năm|sáu|bảy|[2-7])(?![\\p{L}\\d])"
              + "|chủ\\s*nhật"
              + "|vào\\s+ngày\\s+\\d"
              + "|lúc\\s+\\d+\\s*giờ"
              + "|\\d+\\s*(lần|giao dịch|lượt)"
              + "|vào\\s+\\d+\\s*(sáng|chiều|tối)"
              + "|on\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)"
              + "|at\\s+\\d+\\s*(am|pm)"
              + "|\\d+\\s*times?\\b)");

  @Override
  public String checkReply(
      AgentSpec agent, String reply, boolean citedOrToolBacked, boolean memoryUsed) {
    if (reply == null) {
      return null;
    }
    if (PROMPT_LEAK.matcher(reply).find()) {
      throw new BusinessException(ErrorCode.GUARDRAIL, Map.of("reason", "PROMPT_LEAK"));
    }
    if (memoryUsed && SURVEILLANCE.matcher(reply).find()) {
      throw new BusinessException(ErrorCode.GUARDRAIL, Map.of("reason", "SURVEILLANCE_FACT"));
    }
    Guardrails guardrails = agent.guardrails();
    boolean requireCitation = guardrails == null || guardrails.requireCitationForNumbers();
    if (requireCitation && !citedOrToolBacked && NUMBER.matcher(reply).find()) {
      // A rate or an amount with nothing behind it is exactly the failure mode §3.1 forbids.
      throw new BusinessException(
          ErrorCode.GUARDRAIL,
          Map.of(
              "reason",
              "UNCITED_NUMBER",
              "messageKey",
              guardrails == null || guardrails.refusalKey() == null
                  ? "mi.reply.guardrail.offTopic"
                  : guardrails.refusalKey()));
    }
    return sanitiseLinks(stripMarkdown(reply));
  }

  /**
   * The chat bubble is plain {@code Text}, not a markdown renderer — found live: the hosted model
   * answers with {@code **bold**} emphasis, which reached the screen as literal asterisks around
   * the words instead of anything resembling bold. Rule-based and tool-narrated replies never
   * contain this syntax, so stripping it here catches every model that might, present or future.
   */
  private static final Pattern MD_BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
  private static final Pattern MD_ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");

  /** {@code ## Heading} — the hash marks are noise once nothing renders them. */
  private static final Pattern MD_HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s*");

  /** {@code > quoted} at the start of a line. */
  private static final Pattern MD_QUOTE = Pattern.compile("(?m)^\\s{0,3}>\\s?");

  /** A table's {@code |---|---|} rule row, which carries no content at all. */
  private static final Pattern MD_TABLE_RULE = Pattern.compile("(?m)^\\s*\\|?[\\s:|-]{3,}\\|?\\s*$\\n?");

  /** A table row: cells become a readable list, since columns cannot survive a plain text bubble. */
  private static final Pattern MD_TABLE_ROW = Pattern.compile("(?m)^\\s*\\|(.+)\\|\\s*$");

  /**
   * Inline LaTeX the model reaches for around an estimate ({@code $\approx$ 1,8 tỷ đồng}) — found
   * live, on device, in a mortgage-affordability answer. There is no math renderer behind the chat
   * bubble, so this arrived as the literal dollar signs and backslash command rather than "≈".
   */
  private static final Pattern LATEX_COMMAND = Pattern.compile("\\\\(approx|times|div|pm|leq|geq|neq)");
  private static final Map<String, String> LATEX_GLYPHS =
      Map.of("approx", "≈", "times", "×", "div", "÷", "pm", "±", "leq", "≤", "geq", "≥", "neq", "≠");
  /** Only the `$…$` wrapper around a glyph, so a literal currency "$" in a reply survives. */
  private static final Pattern LATEX_DOLLAR = Pattern.compile("\\$\\s*([≈×÷±≤≥≠])\\s*\\$");

  private String stripMarkdown(String reply) {
    String flat = MD_TABLE_RULE.matcher(reply).replaceAll("");
    flat = flattenTableRows(flat);
    flat = MD_HEADING.matcher(flat).replaceAll("");
    flat = MD_QUOTE.matcher(flat).replaceAll("");
    flat = stripLatex(flat);
    String withoutBold = MD_BOLD.matcher(flat).replaceAll("$1");
    return MD_ITALIC.matcher(withoutBold).replaceAll("$1").strip();
  }

  private String stripLatex(String text) {
    Matcher matcher = LATEX_COMMAND.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(
          out, Matcher.quoteReplacement(LATEX_GLYPHS.getOrDefault(matcher.group(1), "")));
    }
    matcher.appendTail(out);
    return LATEX_DOLLAR.matcher(out).replaceAll("$1");
  }

  /**
   * Turns {@code | Kỳ hạn | Lãi suất |} into {@code Kỳ hạn · Lãi suất}.
   *
   * <p>The VRM agent answers rate and fee questions with markdown tables, which arrived on screen as
   * rows of pipes with the columns collapsed — the numbers were all there and unreadable. A middle dot
   * keeps the cells distinguishable in a single line of plain text.
   */
  private String flattenTableRows(String reply) {
    return MD_TABLE_ROW
        .matcher(reply)
        .replaceAll(
            match -> {
              String[] cells = match.group(1).split("\\|", -1);
              StringBuilder row = new StringBuilder();
              for (String cell : cells) {
                String trimmed = cell.strip();
                if (trimmed.isEmpty()) {
                  continue;
                }
                if (!row.isEmpty()) {
                  row.append(" · ");
                }
                row.append(java.util.regex.Matcher.quoteReplacement(trimmed));
              }
              return row.toString();
            });
  }

  @Override
  public void checkProposalAmount(AgentSpec agent, BigDecimal amount) {
    Guardrails guardrails = agent.guardrails();
    if (guardrails == null || guardrails.maxProposalAmount() == null || amount == null) {
      return;
    }
    if (amount.compareTo(guardrails.maxProposalAmount()) > 0) {
      throw new BusinessException(
          ErrorCode.GUARDRAIL,
          Map.of("reason", "AGENT_MAX_AMOUNT", "max", guardrails.maxProposalAmount()));
    }
  }

  @Override
  public String sanitiseLinks(String reply) {
    Matcher matcher = URL.matcher(reply);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String url = matcher.group();
      boolean allowed =
          MiConstants.URL_ALLOWLIST.stream().anyMatch(url::startsWith)
              && (!url.startsWith("msb://")
                  || MiConstants.DEEP_LINK_ROUTES.stream().anyMatch(url::startsWith));
      matcher.appendReplacement(out, Matcher.quoteReplacement(allowed ? url : ""));
    }
    matcher.appendTail(out);
    return out.toString().replaceAll("\\s{2,}", " ").strip();
  }
}
