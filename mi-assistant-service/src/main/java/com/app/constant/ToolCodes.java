package com.app.constant;

import java.util.List;
import java.util.Map;

/**
 * The tool catalogue. A code here is only callable when the CMS has it enabled and lists it in the
 * active agent's {@code toolCodes} (design §3.3).
 */
public final class ToolCodes {

  private ToolCodes() {}

  // ── Mi-local ───────────────────────────────────────────────────────────────
  public static final String SEARCH_KNOWLEDGE = "search_knowledge";
  public static final String ASK_CLARIFICATION = "ask_clarification";
  public static final String HANDOFF = "handoff";
  public static final String REMEMBER = "remember";

  // ── read ───────────────────────────────────────────────────────────────────
  public static final String GET_SPENDING_SUMMARY = "get_spending_summary";
  public static final String GET_ACCOUNT_SUMMARY = "get_account_summary";
  public static final String GET_LOAN_OVERVIEW = "get_loan_overview";
  public static final String CALCULATE_LOAN = "calculate_loan";
  /** Borrowable amount and instalment for a property value, capped by LTV / product / income. */
  public static final String SIZE_MORTGAGE = "size_mortgage";
  /** The reverse question: what property value a given instalment or income can carry. */
  public static final String CHECK_AFFORDABILITY = "check_affordability";
  public static final String QUOTE_PREPAYMENT = "quote_prepayment";
  public static final String GET_SCHEDULE = "get_schedule";
  public static final String GET_CARDS = "get_cards";
  public static final String GET_STATEMENT = "get_statement";
  public static final String GET_CARD_CONTROLS = "get_card_controls";
  public static final String CHECK_CARD_ELIGIBILITY = "check_card_eligibility";
  public static final String GET_RATES = "get_rates";
  public static final String GET_SAVINGS_OVERVIEW = "get_savings_overview";
  public static final String PREVIEW_GOAL = "preview_goal";
  public static final String FIND_BENEFICIARY = "find_beneficiary";
  public static final String GET_DUE_BILLS = "get_due_bills";

  // ── mutating: every one of these only ever creates a PROPOSAL ─────────────
  public static final String SET_BUDGET = "set_budget";
  public static final String PROPOSE_TRANSFER = "propose_transfer";
  public static final String PROPOSE_BILL_PAYMENT = "propose_bill_payment";
  public static final String PROPOSE_DEPOSIT = "propose_deposit";
  public static final String PROPOSE_GOAL_TOPUP = "propose_goal_topup";
  public static final String PROPOSE_CARD_PAYMENT = "propose_card_payment";
  public static final String PROPOSE_LOAN_REPAYMENT = "propose_loan_repayment";
  public static final String PROPOSE_PREPAYMENT = "propose_prepayment";
  public static final String CREATE_GOAL = "create_goal";
  public static final String LOCK_CARD = "lock_card";
  public static final String UNLOCK_CARD = "unlock_card";
  public static final String SET_CARD_CONTROL = "set_card_control";
  /** Opens a card. Always a proposal, never AUTO — see AutonomyPolicy and design 07 §3.2. */
  public static final String PROPOSE_NEW_CARD = "propose_new_card";

  public static final List<String> MUTATING =
      List.of(
          SET_BUDGET,
          PROPOSE_TRANSFER,
          PROPOSE_BILL_PAYMENT,
          PROPOSE_DEPOSIT,
          PROPOSE_GOAL_TOPUP,
          PROPOSE_CARD_PAYMENT,
          PROPOSE_LOAN_REPAYMENT,
          PROPOSE_PREPAYMENT,
          PROPOSE_NEW_CARD,
          CREATE_GOAL,
          LOCK_CARD,
          UNLOCK_CARD,
          SET_CARD_CONTROL);

  /** The step-up scope a confirmed proposal from this tool needs. */
  public static final Map<String, String> STEP_UP_SCOPES =
      Map.of(
          PROPOSE_TRANSFER, AppConstants.STEP_UP_TRANSFER,
          PROPOSE_BILL_PAYMENT, AppConstants.STEP_UP_PAYMENT,
          PROPOSE_CARD_PAYMENT, AppConstants.STEP_UP_PAYMENT,
          PROPOSE_DEPOSIT, AppConstants.STEP_UP_PAYMENT,
          PROPOSE_GOAL_TOPUP, AppConstants.STEP_UP_PAYMENT,
          PROPOSE_LOAN_REPAYMENT, AppConstants.STEP_UP_PAYMENT,
          PROPOSE_PREPAYMENT, AppConstants.STEP_UP_PAYMENT,
          // Issuing a card moves no money, so it carries its own scope rather than `payment`:
          // consent to a credit product is not consent to a payment (design 07 §3.1).
          PROPOSE_NEW_CARD, AppConstants.STEP_UP_CARD_ISSUE);

  /** Fallback allow-list per agent, used only until the CMS answers (design §3.3 table). */
  public static final Map<String, List<String>> DEFAULT_BY_AGENT =
      Map.of(
          AgentCodes.GENERAL,
              List.of(
                  SEARCH_KNOWLEDGE, GET_SPENDING_SUMMARY, GET_ACCOUNT_SUMMARY, SET_BUDGET,
                  ASK_CLARIFICATION, REMEMBER),
          AgentCodes.LOAN,
              List.of(
                  GET_LOAN_OVERVIEW, CALCULATE_LOAN, SIZE_MORTGAGE, CHECK_AFFORDABILITY,
                  QUOTE_PREPAYMENT, GET_SCHEDULE, PROPOSE_LOAN_REPAYMENT, PROPOSE_PREPAYMENT,
                  SEARCH_KNOWLEDGE),
          AgentCodes.CARD,
              List.of(
                  GET_CARDS, GET_STATEMENT, GET_CARD_CONTROLS, CHECK_CARD_ELIGIBILITY,
                  PROPOSE_CARD_PAYMENT, PROPOSE_NEW_CARD, LOCK_CARD, UNLOCK_CARD,
                  SET_CARD_CONTROL, SEARCH_KNOWLEDGE, HANDOFF),
          AgentCodes.SAVING,
              List.of(
                  GET_RATES, GET_SAVINGS_OVERVIEW, PREVIEW_GOAL, PROPOSE_DEPOSIT,
                  PROPOSE_GOAL_TOPUP, CREATE_GOAL, SEARCH_KNOWLEDGE),
          AgentCodes.PAYMENT,
              List.of(
                  FIND_BENEFICIARY, GET_DUE_BILLS, PROPOSE_TRANSFER, PROPOSE_BILL_PAYMENT,
                  SEARCH_KNOWLEDGE));
}
