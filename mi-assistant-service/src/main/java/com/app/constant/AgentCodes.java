package com.app.constant;

import java.util.List;

/** Agent codes. The definitions themselves live in the CMS (design §3.3). */
public final class AgentCodes {

  private AgentCodes() {}

  public static final String ROUTER = "router";
  public static final String GENERAL = "general";
  public static final String LOAN = "loan";
  public static final String CARD = "card";
  public static final String SAVING = "saving";
  public static final String PAYMENT = "payment";

  public static final String DOMAIN_ROUTER = "ROUTER";
  public static final String DOMAIN_GENERAL = "GENERAL";
  public static final String DOMAIN_LOAN = "LOAN";
  public static final String DOMAIN_CARD = "CARD";
  public static final String DOMAIN_SAVING = "SAVING";
  public static final String DOMAIN_PAYMENT = "PAYMENT";

  public static final List<String> DOMAINS =
      List.of(DOMAIN_GENERAL, DOMAIN_LOAN, DOMAIN_CARD, DOMAIN_SAVING, DOMAIN_PAYMENT);

  /** The agent that owns a domain when the CMS has not overridden the mapping. */
  public static String defaultAgentFor(String domain) {
    return switch (domain) {
      case DOMAIN_LOAN -> LOAN;
      case DOMAIN_CARD -> CARD;
      case DOMAIN_SAVING -> SAVING;
      case DOMAIN_PAYMENT -> PAYMENT;
      default -> GENERAL;
    };
  }
}
