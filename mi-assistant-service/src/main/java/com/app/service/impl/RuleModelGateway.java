package com.app.service.impl;

import com.app.constant.AgentCodes;
import com.app.constant.MiConstants;
import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.ModelReply;
import com.app.dto.internal.MiInternal.RouteDecision;
import com.app.dto.internal.MiInternal.ToolSpec;
import com.app.service.model.ModelGateway;
import com.app.service.tool.AmountParser;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

/**
 * The deterministic gateway. It reads the turn with the same rules the evaluation sets encode:
 * which domain it belongs to, which tool it wants and with what arguments.
 *
 * <p>It is the default because the whole safety story of §12 is asserted against something
 * reproducible, and because the demo has to work with no model key. Switching
 * {@code app.mi.llm.provider} to {@code openai} replaces it without touching a caller.
 *
 * <p>It stays registered either way: a small local model often cannot call tools at all, and
 * {@link OpenAiModelGateway} falls back to this for tool selection when that is the case.
 */
@Service
public class RuleModelGateway implements ModelGateway {

  /**
   * Domain phrases with the weight of the signal they carry. Weight matters because turns mix
   * vocabularies: "trả trước 20 triệu tiết kiệm bao nhiêu lãi" names both a loan action and the
   * word for saving, and only one of them is what the customer wants.
   */
  private record Marker(String phrase, String domain, int weight) {}

  private static final List<Marker> DOMAIN_MARKERS =
      List.of(
          // payment
          new Marker("chuyen tien", AgentCodes.DOMAIN_PAYMENT, 3),
          new Marker("chuyen khoan", AgentCodes.DOMAIN_PAYMENT, 3),
          new Marker("hoa don", AgentCodes.DOMAIN_PAYMENT, 3),
          new Marker("transfer", AgentCodes.DOMAIN_PAYMENT, 3),
          new Marker("chuyen", AgentCodes.DOMAIN_PAYMENT, 2),
          new Marker("thanh toan", AgentCodes.DOMAIN_PAYMENT, 2),
          new Marker("tien dien", AgentCodes.DOMAIN_PAYMENT, 3),
          new Marker("tien nuoc", AgentCodes.DOMAIN_PAYMENT, 3),
          // loan — these outrank the savings vocabulary a prepayment question also uses
          new Marker("tra truoc", AgentCodes.DOMAIN_LOAN, 5),
          new Marker("tra gop", AgentCodes.DOMAIN_LOAN, 5),
          new Marker("khoan vay", AgentCodes.DOMAIN_LOAN, 5),
          new Marker("du no", AgentCodes.DOMAIN_LOAN, 5),
          new Marker("tat toan", AgentCodes.DOMAIN_LOAN, 5),
          new Marker("vay", AgentCodes.DOMAIN_LOAN, 3),
          new Marker("loan", AgentCodes.DOMAIN_LOAN, 3),
          new Marker("lai suat vay", AgentCodes.DOMAIN_LOAN, 5),
          // card
          new Marker("sao ke", AgentCodes.DOMAIN_CARD, 5),
          new Marker("khoa the", AgentCodes.DOMAIN_CARD, 5),
          new Marker("mo khoa the", AgentCodes.DOMAIN_CARD, 5),
          new Marker("the tin dung", AgentCodes.DOMAIN_CARD, 5),
          new Marker("the cua toi", AgentCodes.DOMAIN_CARD, 5),
          new Marker("han muc the", AgentCodes.DOMAIN_CARD, 5),
          new Marker("the ghi no", AgentCodes.DOMAIN_CARD, 5),
          new Marker("card", AgentCodes.DOMAIN_CARD, 3),
          // Folding drops the diacritics that separate "thẻ" from "thế", so the bare word is a
          // card only when the next word is not one of the "thế …" fillers.
          new Marker("the", AgentCodes.DOMAIN_CARD, 2),
          // saving
          new Marker("tiet kiem", AgentCodes.DOMAIN_SAVING, 3),
          new Marker("so tiet kiem", AgentCodes.DOMAIN_SAVING, 4),
          new Marker("ky han", AgentCodes.DOMAIN_SAVING, 3),
          new Marker("lai suat", AgentCodes.DOMAIN_SAVING, 3),
          new Marker("muc tieu", AgentCodes.DOMAIN_SAVING, 3),
          new Marker("dao han", AgentCodes.DOMAIN_SAVING, 3),
          new Marker("saving", AgentCodes.DOMAIN_SAVING, 3),
          new Marker("deposit", AgentCodes.DOMAIN_SAVING, 3),
          // general
          new Marker("tro chuyen", AgentCodes.DOMAIN_GENERAL, 6),
          new Marker("chi tieu", AgentCodes.DOMAIN_GENERAL, 3),
          new Marker("so du", AgentCodes.DOMAIN_GENERAL, 3),
          new Marker("tai khoan", AgentCodes.DOMAIN_GENERAL, 3),
          new Marker("diem thuong", AgentCodes.DOMAIN_GENERAL, 3),
          new Marker("spending", AgentCodes.DOMAIN_GENERAL, 3),
          new Marker("balance", AgentCodes.DOMAIN_GENERAL, 3));

  private final AmountParser amountParser;
  private final MessageSource messages;

  public RuleModelGateway(AmountParser amountParser, MessageSource messages) {
    this.messages = messages;
    this.amountParser = amountParser;
  }

  @Override
  public ModelReply chat(
      AgentSpec agent,
      String systemPrompt,
      List<Turn> history,
      String userTurn,
      List<ToolSpec> toolsAvailable,
      Locale locale) {

    String folded = fold(userTurn);
    List<String> allowed = toolsAvailable.stream().map(ToolSpec::code).toList();
    boolean transferIntent =
        mentions(folded, "chuyen", "transfer", "send")
            || (mentions(folded, "gui") && mentions(folded, " cho "));

    // A transfer needs a bank, an account number and an amount, and a customer rarely gives all three
    // in one message: "Tôi muốn chuyển tiền" is the usual opening. What they have said so far is read
    // back out of the conversation's working memory, so each reply adds to the same draft and MEE asks
    // only for what is still missing. Nothing is proposed until the draft is complete, and a customer
    // who asks about transferring (fees, limits, how) is answered from the knowledge base instead.
    if (allowed.contains(ToolCodes.PROPOSE_TRANSFER)) {
      ModelReply transfer = transferReply(history, userTurn, transferIntent, locale);
      if (transfer != null) {
        return transfer;
      }
    }
    BigDecimal amount = amountParser.parse(userTurn);
    // "Hoá đơn nào sắp đến hạn?" asks; "Thanh toán hoá đơn tuần này" instructs. Only the second
    // should reach a proposal — answering a question with a payment card is a nasty surprise.
    boolean asking = isQuestion(userTurn, folded);

    // Money intents first: they are the ones that must resolve to a validated tool call, never to
    // free text the customer could mistake for a confirmation.
    if (allowed.contains(ToolCodes.PROPOSE_BILL_PAYMENT)
        && !asking
        && mentions(folded, "hoa don", "bill", "dien nuoc", "tien dien")) {
      return new ModelReply(null, ToolCodes.PROPOSE_BILL_PAYMENT, Map.of("withinDays", 7), "rules");
    }
    boolean depositIntent = mentions(folded, "so tiet kiem", "gui tiet kiem", "mo so", "deposit");
    if (amount == null
        && depositIntent
        && allowed.contains(ToolCodes.PROPOSE_DEPOSIT)
        && !aboutTheProduct(folded)) {
      return new ModelReply(
          messages.getMessage("mi.reply.deposit.askAmount", null, locale), null, Map.of(), "rules");
    }
    if (amount != null && allowed.contains(ToolCodes.PROPOSE_DEPOSIT) && depositIntent) {
      Map<String, Object> args = new HashMap<>();
      args.put("amount", amount);
      args.put("termMonths", termMonths(folded));
      return new ModelReply(null, ToolCodes.PROPOSE_DEPOSIT, args, "rules");
    }
    if (!asking
        && allowed.contains(ToolCodes.PROPOSE_CARD_PAYMENT)
        && mentions(folded, "tra the", "thanh toan the", "pay card", "thanh toan sao ke")) {
      Map<String, Object> args = new HashMap<>();
      args.put("option", "FULL");
      args.put("amount", amount);
      return new ModelReply(null, ToolCodes.PROPOSE_CARD_PAYMENT, args, "rules");
    }
    // "Trả trước 20 triệu thì tiết kiệm bao nhiêu lãi?" asks what it would save; answering with a
    // repayment card would put money-moving one tap away from a question. Only the imperative
    // proposes — the interrogative falls through to quote_prepayment below.
    if (amount != null
        && !asking
        && allowed.contains(ToolCodes.PROPOSE_PREPAYMENT)
        && mentions(folded, "tra truoc", "prepay", "tat toan")) {
      return new ModelReply(
          null, ToolCodes.PROPOSE_PREPAYMENT, Map.of("amount", amount), "rules");
    }
    if (amount != null
        && allowed.contains(ToolCodes.PROPOSE_GOAL_TOPUP)
        && mentions(folded, "muc tieu", "goal", "nap vao")) {
      return new ModelReply(null, ToolCodes.PROPOSE_GOAL_TOPUP, Map.of("amount", amount), "rules");
    }

    // "Làm sao để khoá thẻ?" is a question about a card but wants the guide, not the card list.
    boolean howTo = mentions(folded, "lam sao", "lam the nao", "cach ", "huong dan", "how to",
        "how do");

    // A data tool answers questions about the customer's own holdings. "Phí rút tiền mặt là bao
    // nhiêu?" is about the product, not about them, and belongs to the knowledge base — reaching
    // for their card list there answers a question nobody asked.
    boolean aboutMe =
        mentions(folded, "cua toi", "toi con", "toi co", "cua minh", " my ", "minh con",
            "con bao nhieu", "con lai", "sap den han", "den han", "hien tai", "thang nay");

    // Any other question inside a domain is answered from that domain's data, not from a page.
    if (!howTo && asking && aboutMe && allowed.contains(ToolCodes.GET_DUE_BILLS)
        && mentions(folded, "hoa don", "bill")) {
      return new ModelReply(null, ToolCodes.GET_DUE_BILLS, Map.of(), "rules");
    }
    if (!howTo && asking && aboutMe && allowed.contains(ToolCodes.GET_LOAN_OVERVIEW)
        && mentions(folded, "khoan vay", "du no", "vay", "tra gop")) {
      return new ModelReply(null, ToolCodes.GET_LOAN_OVERVIEW, Map.of(), "rules");
    }
    // "Sao kê thẻ của tôi bao nhiêu?" wants the amount due, which only the statement carries;
    // the card list would answer a question about the plastic instead.
    if (!howTo && asking && aboutMe && allowed.contains(ToolCodes.GET_STATEMENT)
        && mentions(folded, "sao ke", "statement", "du no the", "phai tra the")) {
      return new ModelReply(null, ToolCodes.GET_STATEMENT, Map.of(), "rules");
    }
    if (!howTo && asking && aboutMe && allowed.contains(ToolCodes.GET_CARDS)
        && mentions(folded, "the", "han muc", "sao ke", "card")) {
      return new ModelReply(null, ToolCodes.GET_CARDS, Map.of(), "rules");
    }

    // Read intents. A "làm sao" turn wants the guide even when it names the customer's own
    // holdings — "Làm sao để khoá thẻ của tôi?" is a how-to, not a request for the card list.
    if (!howTo
        && allowed.contains(ToolCodes.GET_SPENDING_SUMMARY)
        && mentions(folded, "chi tieu", "spending", "tieu bao nhieu", "thang nay", "tieu nhieu",
            "tieu nhat", "danh muc", "category")) {
      return new ModelReply(null, ToolCodes.GET_SPENDING_SUMMARY, Map.of(), "rules");
    }
    if (!howTo
        && (!asking || aboutMe)
        && allowed.contains(ToolCodes.GET_ACCOUNT_SUMMARY)
        && mentions(folded, "so du", "balance", "tai khoan")) {
      return new ModelReply(null, ToolCodes.GET_ACCOUNT_SUMMARY, Map.of(), "rules");
    }
    if (!howTo && allowed.contains(ToolCodes.GET_LOAN_OVERVIEW)
        && mentions(folded, "khoan vay", "du no")) {
      return new ModelReply(null, ToolCodes.GET_LOAN_OVERVIEW, Map.of(), "rules");
    }
    // An amount plus a term is a calculator question, not a knowledge one: the customer wants the
    // instalment, and the knowledge base can only explain how instalments work in general.
    if (allowed.contains(ToolCodes.CALCULATE_LOAN)
        && amount != null
        && termMonths(folded) != null
        && mentions(folded, "vay", "tra gop", "borrow", "loan")
        && mentions(folded, "moi thang", "mot thang", "hang thang", "1 thang", "per month",
            "monthly")) {
      Map<String, Object> args = new HashMap<>();
      args.put("principal", amount);
      args.put("termMonths", termMonths(folded));
      return new ModelReply(null, ToolCodes.CALCULATE_LOAN, args, "rules");
    }
    if (allowed.contains(ToolCodes.QUOTE_PREPAYMENT) && amount != null
        && mentions(folded, "tra truoc", "tiet kiem bao nhieu lai")) {
      return new ModelReply(null, ToolCodes.QUOTE_PREPAYMENT, Map.of("amount", amount), "rules");
    }
    if (!howTo && allowed.contains(ToolCodes.GET_CARDS)
        && mentions(folded, "the cua toi", "my cards")) {
      return new ModelReply(null, ToolCodes.GET_CARDS, Map.of(), "rules");
    }
    if (!asking && allowed.contains(ToolCodes.GET_RATES) && mentions(folded, "lai suat", "rate")) {
      return new ModelReply(null, ToolCodes.GET_RATES, Map.of(), "rules");
    }
    if (!asking && allowed.contains(ToolCodes.GET_DUE_BILLS)
        && mentions(folded, "hoa don", "den han")) {
      return new ModelReply(null, ToolCodes.GET_DUE_BILLS, Map.of(), "rules");
    }

    java.util.Optional<ModelReply> smallTalk = smallTalk(userTurn, locale);
    if (smallTalk.isPresent()) {
      return smallTalk.get();
    }

    // Anything else is a knowledge question, which is Level 1's whole point.
    return new ModelReply(null, ToolCodes.SEARCH_KNOWLEDGE, Map.of("query", userTurn), "rules");
  }

  /**
   * Fixed replies for the handful of conversational turns that need no lookup and never change —
   * checked directly by {@link OpenAiModelGateway} before it calls the network, not only when the
   * whole gateway falls back to rules. Found live: the hosted chat model answered "Cảm ơn Mi nhé"
   * and "Mi ơi bạn có thể làm gì?" with stray digits and punctuation glued mid-word and a
   * duplicated closing word — small-model noise a customer would read as Mi being broken. Fixed
   * copy cannot garble.
   */
  public java.util.Optional<ModelReply> smallTalk(String userTurn, Locale locale) {
    String folded = fold(userTurn);
    if (mentions(folded, "cam on", "thank you", "thanks", "cam on nhieu", "cam on ban")) {
      return java.util.Optional.of(
          new ModelReply(messages.getMessage("mi.reply.thanks", null, locale), null, Map.of(),
              "rules"));
    }
    // "Xin chào" had no rule and fell through to "mình chưa có thông tin về việc này".
    String[] words = folded.trim().split("\\s+");
    if (words.length <= 4
        && (containsWord(folded, "chao") || containsWord(folded, "hello") || containsWord(folded, "hi")
            || containsWord(folded, "hey") || containsWord(folded, "alo"))) {
      return java.util.Optional.of(
          new ModelReply(messages.getMessage("mi.reply.greeting", null, locale), null, Map.of(),
              "rules"));
    }
    if (mentions(folded, "co the lam gi", "lam duoc gi", "lam gi cho toi", "what can you do",
        "help me with")) {
      return java.util.Optional.of(new ModelReply(
          messages.getMessage("mi.reply.capabilities", null, locale), null, Map.of(), "rules"));
    }
    return java.util.Optional.empty();
  }

  @Override
  public RouteDecision classify(String text, List<Turn> history, Locale locale) {
    String folded = fold(text);

    Map<String, Integer> scores = new HashMap<>();
    int best = 0;
    for (Marker marker : DOMAIN_MARKERS) {
      if (!containsWord(folded, marker.phrase())) {
        continue;
      }
      if ("the".equals(marker.phrase()) && onlyAsFiller(folded)) {
        continue;
      }
      int score = scores.merge(marker.domain(), marker.weight(), Integer::sum);
      best = Math.max(best, score);
    }

    // A transfer is a verb plus a recipient, which no single word catches: "gửi 1tr5 cho Huy"
    // names neither "chuyển tiền" nor a beneficiary the dictionary knows.
    if (containsWord(folded, "cho")
        && (containsWord(folded, "gui") || containsWord(folded, "chuyen"))
        && !scores.containsKey(AgentCodes.DOMAIN_SAVING)) {
      int score = scores.merge(AgentCodes.DOMAIN_PAYMENT, 4, Integer::sum);
      best = Math.max(best, score);
    }

    if (best == 0) {
      return new RouteDecision(AgentCodes.DOMAIN_GENERAL, 0.30, summarise(text));
    }
    String domain =
        scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(AgentCodes.DOMAIN_GENERAL);

    // Confidence rises with the weight of the evidence and with how clearly it beat the runner-up.
    int runnerUp =
        scores.entrySet().stream()
            .filter(e -> !e.getKey().equals(domain))
            .mapToInt(Map.Entry::getValue)
            .max()
            .orElse(0);
    double confidence = Math.min(0.95, 0.55 + 0.05 * best + 0.05 * (best - runnerUp));
    return new RouteDecision(domain, confidence, summarise(text));
  }

  /**
   * True when every bare "the" in the turn is the Vietnamese filler ("thế nào", "thế thì", "như
   * thế") rather than the noun "thẻ". Without this "Thời tiết hôm nay thế nào?" routes to the
   * card agent and gets answered from the card knowledge base.
   */
  private boolean onlyAsFiller(String folded) {
    String[] fillers = {"the nao", "the thi", "nhu the", "the a", "the co"};
    int occurrences = 0;
    int from = 0;
    while (true) {
      int at = folded.indexOf("the", from);
      if (at < 0) {
        break;
      }
      boolean leftOk = at == 0 || !Character.isLetterOrDigit(folded.charAt(at - 1));
      int after = at + 3;
      boolean rightOk = after >= folded.length() || !Character.isLetterOrDigit(folded.charAt(after));
      if (leftOk && rightOk) {
        occurrences++;
        boolean filler = false;
        for (String phrase : fillers) {
          int start = phrase.startsWith("the") ? at : Math.max(0, at - (phrase.length() - 3));
          if (folded.startsWith(phrase, start)) {
            filler = true;
            break;
          }
        }
        if (!filler) {
          return false;
        }
      }
      from = at + 1;
    }
    return occurrences > 0;
  }

  /** Substring matching made "thẻ" fire inside "thế nào"; markers must match whole words. */
  private boolean containsWord(String folded, String phrase) {
    int from = 0;
    while (true) {
      int at = folded.indexOf(phrase, from);
      if (at < 0) {
        return false;
      }
      boolean leftOk = at == 0 || !Character.isLetterOrDigit(folded.charAt(at - 1));
      int after = at + phrase.length();
      boolean rightOk =
          after >= folded.length() || !Character.isLetterOrDigit(folded.charAt(after));
      if (leftOk && rightOk) {
        return true;
      }
      from = at + 1;
    }
  }

  private String summarise(String text) {
    String trimmed = text == null ? "" : text.strip();
    int limit = MiConstants.HANDOFF_SUMMARY_TOKENS * MiConstants.CHARS_PER_TOKEN;
    return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit) + "…";
  }

  /** Interrogative unless the turn opens with an instruction to act. */
  private boolean isQuestion(String raw, String folded) {
    if (mentions(folded, "thanh toan", "chuyen", "tra tien", "mo so", "nap", "gui cho")) {
      return false;
    }
    return raw.contains("?")
        || mentions(folded, "bao nhieu", "the nao", "nao ", "khi nao", "co the", "lam sao", "how ",
            "what ", "when ");
  }

  private boolean mentions(String folded, String... markers) {
    for (String marker : markers) {
      if (folded.contains(marker)) {
        return true;
      }
    }
    return false;
  }

  /** The bank codes transfer-service knows, by every way a customer is likely to write them. */
  private static final Map<String, String> BANK_ALIASES =
      Map.ofEntries(
          Map.entry("msb", "MSB"), Map.entry("maritime", "MSB"), Map.entry("maritimebank", "MSB"),
          Map.entry("vcb", "VCB"), Map.entry("vietcombank", "VCB"),
          Map.entry("tcb", "TCB"), Map.entry("techcombank", "TCB"),
          Map.entry("bidv", "BIDV"),
          Map.entry("vpb", "VPB"), Map.entry("vpbank", "VPB"),
          Map.entry("acb", "ACB"),
          Map.entry("agr", "AGR"), Map.entry("agribank", "AGR"));

  /** Words that sit next to an account number without naming the bank. */
  private static final java.util.Set<String> ACCOUNT_FILLER =
      java.util.Set.of("tk", "stk", "so", "tai", "khoan", "account", "acc", "ngan", "hang", "bank",
          "nh", "vao", "cho", "den", "toi", "sang", "to", "cua", "nguoi", "nhan", "va", "nhe", "nha",
          "giup", "minh", "mi", "voi", "di", "chuyen", "muon", "gui", "tien", "dong", "vnd", "trieu",
          "nghin", "ty", "tr", "ban", "can");

  /** A run of digits long enough to be an account number, and not an amount written with a unit. */
  private static final java.util.regex.Pattern ACCOUNT_NUMBER =
      java.util.regex.Pattern.compile(
          "(?<![\\d.,])(\\d{6,19})(?![\\d.,]*\\d)(?!\\s*(?:tr|trieu|ty|ti|k|m|nghin|ngan|d|dong|vnd)\\b)");

  /** Where the recipient starts: the first of these words in the message. */
  private static final List<String> RECIPIENT_MARKERS =
      List.of(" cho ", " sang ", " to ", " vao ", " stk ", " tk ", " tai khoan ");

  /** What one message told us about a transfer. Any part can be missing. */
  private record TransferSlots(String bank, String account, BigDecimal amount, String recipient) {
    static final TransferSlots EMPTY = new TransferSlots(null, null, null, null);

    boolean isEmpty() {
      return bank == null && account == null && amount == null && recipient == null;
    }

    /** A later message wins, so "à nhầm, 5 triệu thôi" corrects the amount. */
    TransferSlots overlay(TransferSlots later) {
      return new TransferSlots(
          later.bank != null ? later.bank : bank,
          later.account != null ? later.account : account,
          later.amount != null ? later.amount : amount,
          later.recipient != null ? later.recipient : recipient);
    }
  }

  private ModelReply text(String message) {
    return new ModelReply(message, null, Map.of(), "rules");
  }

  /**
   * The reply to a transfer instruction: a proposal when the draft is complete, the question that
   * gets the missing details when it is not, or {@code null} when the turn is not a transfer request
   * at all (so a question about fees still reaches the knowledge base).
   */
  private ModelReply transferReply(
      List<Turn> history, String userTurn, boolean transferIntent, Locale locale) {
    String folded = fold(userTurn);
    List<String> earlier = openTransferTurns(history, userTurn, locale);
    boolean followUp = !earlier.isEmpty();
    if (!followUp && (!transferIntent || aboutTheProduct(folded))) {
      return null;
    }

    TransferSlots now = extractSlots(userTurn);
    if (followUp) {
      if (containsWord(folded, "huy") || containsWord(folded, "cancel")
          || mentions(folded, "khong chuyen", "thoi khong", "dung chuyen", "bo qua")) {
        return text(messages.getMessage("mi.reply.cancelled", null, locale));
      }
      // A real question in the middle of the exchange is answered from the knowledge base. Its answer
      // ends the exchange (MEE's last reply is no longer a request for details), so the customer picks
      // the transfer up again with the details rather than being pulled back into it.
      if (aboutTheProduct(folded) && now.isEmpty()) {
        return null;
      }
    }

    TransferSlots slots = TransferSlots.EMPTY;
    for (String turn : earlier) {
      slots = slots.overlay(extractSlots(turn));
    }
    slots = slots.overlay(now);

    boolean typedAccount = slots.account() != null || slots.bank() != null;
    List<String> missing = new ArrayList<>();
    if (typedAccount) {
      if (slots.bank() == null) {
        missing.add(messages.getMessage("mi.transfer.slot.bank", null, locale));
      }
      if (slots.account() == null) {
        missing.add(messages.getMessage("mi.transfer.slot.account", null, locale));
      }
    } else if (slots.recipient() == null) {
      missing.add(messages.getMessage("mi.transfer.slot.bank", null, locale));
      missing.add(messages.getMessage("mi.transfer.slot.account", null, locale));
    }
    if (slots.amount() == null) {
      missing.add(messages.getMessage("mi.transfer.slot.amount", null, locale));
    }

    if (missing.isEmpty()) {
      Map<String, Object> args = new HashMap<>();
      args.put("amount", slots.amount());
      if (typedAccount) {
        args.put("bankCode", slots.bank());
        args.put("accountNumber", slots.account());
      } else {
        args.put("recipientQuery", slots.recipient());
      }
      args.put("note", null);
      return new ModelReply(null, ToolCodes.PROPOSE_TRANSFER, args, "rules");
    }

    List<String> known = new ArrayList<>();
    if (slots.recipient() != null && !typedAccount) {
      known.add(messages.getMessage("mi.transfer.known.recipient", new Object[] {slots.recipient()}, locale));
    }
    if (slots.bank() != null) {
      known.add(messages.getMessage("mi.transfer.known.bank", new Object[] {slots.bank()}, locale));
    }
    if (slots.account() != null) {
      known.add(messages.getMessage("mi.transfer.known.account", new Object[] {slots.account()}, locale));
    }
    if (slots.amount() != null) {
      String formatted = java.text.NumberFormat.getIntegerInstance(locale).format(slots.amount());
      known.add(messages.getMessage("mi.transfer.known.amount", new Object[] {formatted}, locale));
    }
    if (known.isEmpty()) {
      return text(messages.getMessage("mi.reply.transfer.collect", null, locale));
    }
    return text(
        messages.getMessage(
            "mi.reply.transfer.collectMissing",
            new Object[] {String.join(", ", known), String.join(", ", missing)},
            locale));
  }

  /**
   * The customer's earlier messages in a transfer exchange that is still open: the run of
   * (customer, MEE-asked-for-transfer-details) pairs immediately before this message. Empty when MEE's
   * last reply was anything else, which is also how a finished or abandoned exchange stops counting.
   */
  private List<String> openTransferTurns(List<Turn> history, String userTurn, Locale locale) {
    List<String[]> entries = new ArrayList<>();
    for (Turn turn : history == null ? List.<Turn>of() : history) {
      String line = turn.text() == null ? "" : turn.text();
      int cut = line.indexOf(": ");
      if (cut > 0) {
        entries.add(new String[] {line.substring(0, cut), line.substring(cut + 2)});
      }
    }
    int end = entries.size();
    // Working memory already holds the message being answered; it is the current turn, not history.
    if (end > 0
        && MiConstants.ROLE_USER.equals(entries.get(end - 1)[0])
        && entries.get(end - 1)[1].strip().equals(userTurn == null ? "" : userTurn.strip())) {
      end--;
    }
    List<String> chain = new ArrayList<>();
    int i = end - 1;
    while (i >= 1) {
      String[] reply = entries.get(i);
      String[] asked = entries.get(i - 1);
      if (!MiConstants.ROLE_MI.equals(reply[0])
          || !isTransferAsk(reply[1], locale)
          || !MiConstants.ROLE_USER.equals(asked[0])) {
        break;
      }
      chain.add(0, asked[1]);
      i -= 2;
    }
    return chain;
  }

  /** Whether a stored MEE reply is one of the questions that collect transfer details. */
  private boolean isTransferAsk(String reply, Locale locale) {
    for (String key : List.of("mi.reply.transfer.collect", "mi.reply.transfer.collectMissing")) {
      String template = messages.getMessage(key, null, key, locale);
      int placeholder = template.indexOf('{');
      String prefix =
          placeholder > 0 ? template.substring(0, placeholder) : template.substring(0, Math.min(30, template.length()));
      if (!prefix.isBlank() && reply.strip().startsWith(prefix.strip())) {
        return true;
      }
    }
    return false;
  }

  /** What a single message says about the transfer: the bank, the account, the amount, a saved name. */
  private TransferSlots extractSlots(String text) {
    // Padded so a reply that starts with the marker ("cho mẹ") is found like one that does not.
    String folded = " " + fold(text);
    int marker = -1;
    for (String candidate : RECIPIENT_MARKERS) {
      int index = markerIndex(folded, candidate);
      if (index >= 0 && (marker < 0 || index < marker)) {
        marker = index;
      }
    }

    // An account number is digits after "cho"/"sang"/"tk", or, when the message has no such word (a
    // reply to "which account?"), digits that look like one. Digits before "cho" are the amount:
    // "chuyển 10000000 cho mẹ" is ten million, not an account.
    String account = null;
    int accountStart = -1;
    int accountEnd = -1;
    java.util.regex.Matcher digits = ACCOUNT_NUMBER.matcher(folded);
    while (digits.find()) {
      String run = digits.group(1);
      boolean afterMarker = marker >= 0 && digits.start() > marker;
      boolean accountShaped = run.length() >= 9 || run.startsWith("0");
      if (afterMarker || (marker < 0 && accountShaped)) {
        account = run;
        accountStart = digits.start();
        accountEnd = digits.end();
        break;
      }
    }

    String bank = null;
    String bankWord = null;
    if (account != null) {
      String scope = folded.substring(marker >= 0 && marker < accountStart ? marker : 0, accountStart);
      bankWord = lastWord(scope);
      if (bankWord == null) {
        bankWord = firstWord(folded.substring(accountEnd));
      }
      bank = bankWord == null ? null : bankCode(bankWord);
    }
    if (bank == null) {
      for (String word : folded.split("[^a-z]+")) {
        String exact = BANK_ALIASES.get(word);
        if (exact != null) {
          bank = exact;
          bankWord = word;
          break;
        }
      }
    }

    // The amount is what is left once the account number and the bank's name are out of the way, or
    // a bare "chuyển cho vcb 0123456789" would be an amount of 123.456.789 đồng.
    String rest = folded;
    if (account != null) {
      rest = rest.substring(0, accountStart) + " " + rest.substring(accountEnd);
    }
    if (bankWord != null && bank != null) {
      rest = rest.replaceFirst("\\b" + java.util.regex.Pattern.quote(bankWord) + "\\b", " ");
    }
    BigDecimal amount = amountParser.parse(rest);

    String recipient = account == null && bank == null ? savedRecipient(text) : null;
    return new TransferSlots(bank, account, amount, recipient);
  }

  /** Verbs that follow an infinitive "to": "I want to send money" names no recipient. */
  private static final java.util.Set<String> INFINITIVE_VERBS =
      java.util.Set.of("send", "transfer", "make", "pay", "move", "wire", "do", "give", "top", "open");

  /**
   * Where a recipient marker first occurs, skipping an English " to " that is only an infinitive
   * ("want to send"): the marker means "to someone", so a verb right after it rules it out.
   */
  private int markerIndex(String spaced, String marker) {
    int from = 0;
    while (true) {
      int index = spaced.indexOf(marker, from);
      if (index < 0 || !" to ".equals(marker)) {
        return index;
      }
      String after = spaced.substring(index + marker.length()).trim();
      String next = after.isEmpty() ? "" : after.split("[^a-z]+")[0];
      if (!INFINITIVE_VERBS.contains(next)) {
        return index;
      }
      from = index + 1;
    }
  }

  /** "chuyển 2 triệu cho anh Minh" → "anh Minh": what follows "cho", "sang" or "to", as typed. */
  private String savedRecipient(String text) {
    String spaced = " " + fold(text);
    int best = -1;
    int bestLength = 0;
    for (String marker : List.of(" cho ", " sang ", " to ")) {
      int index = markerIndex(spaced, marker);
      if (index >= 0 && (best < 0 || index < best)) {
        best = index;
        bestLength = marker.length();
      }
    }
    if (best < 0) {
      return null;
    }
    // `spaced` is one character longer than `text`, so its index is off by one against the original.
    int from = best - 1 + bestLength;
    String name = from >= text.length() ? "" : text.substring(from).strip();
    return name.isBlank() ? null : name;
  }

  private String lastWord(String text) {
    String[] words = text.trim().split("[^a-z]+");
    for (int i = words.length - 1; i >= 0; i--) {
      if (words[i].length() >= 2 && !ACCOUNT_FILLER.contains(words[i])) {
        return words[i];
      }
    }
    return null;
  }

  private String firstWord(String text) {
    for (String word : text.trim().split("[^a-z]+")) {
      if (word.length() >= 2 && !ACCOUNT_FILLER.contains(word)) {
        return word;
      }
    }
    return null;
  }

  /**
   * The bank a typed word means. An exact name wins. Failing that, a three- or four-letter word one
   * keystroke away from exactly one bank code counts ("mab" → MSB); anything else is unknown and the
   * customer is asked. The confirmation card names the bank either way, so a near miss can be caught
   * before any money moves.
   */
  private String bankCode(String token) {
    String exact = BANK_ALIASES.get(token);
    if (exact != null) {
      return exact;
    }
    if (token.length() < 3 || token.length() > 4) {
      return null;
    }
    java.util.Set<String> near = new java.util.HashSet<>();
    for (Map.Entry<String, String> alias : BANK_ALIASES.entrySet()) {
      if (alias.getKey().length() <= 4 && withinOneEdit(token, alias.getKey())) {
        near.add(alias.getValue());
      }
    }
    return near.size() == 1 ? near.iterator().next() : null;
  }

  private boolean withinOneEdit(String a, String b) {
    if (Math.abs(a.length() - b.length()) > 1) {
      return false;
    }
    int i = 0;
    int j = 0;
    int edits = 0;
    while (i < a.length() && j < b.length()) {
      if (a.charAt(i) == b.charAt(j)) {
        i++;
        j++;
        continue;
      }
      if (++edits > 1) {
        return false;
      }
      if (a.length() > b.length()) {
        i++;
      } else if (a.length() < b.length()) {
        j++;
      } else {
        i++;
        j++;
      }
    }
    return edits + (a.length() - i) + (b.length() - j) <= 1;
  }

  /** A question about the product (fees, limits, how it works), as opposed to an instruction. */
  private boolean aboutTheProduct(String folded) {
    return mentions(folded, "phi ", "phi?", "han muc", "lai suat", "bao nhieu", "la gi", "the nao",
        "lam sao", "cach ", "huong dan", "khi nao", "tai sao", "toi da", "how ", "what ", "fee",
        "limit");
  }

  private Integer termMonths(String folded) {
    for (int term : List.of(1, 3, 6, 9, 12, 18, 24, 36)) {
      if (folded.contains(term + " thang") || folded.contains(term + " month")) {
        return term;
      }
    }
    return null;
  }

  private String fold(String text) {
    if (text == null) {
      return "";
    }
    return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replace('đ', 'd')
        .replaceAll("\\s+", " ");
  }
}
