package com.app.constant;

/** The topics this consumer group subscribes to (design §5). */
public final class Topics {

  private Topics() {}

  public static final String ACCOUNT = "msb.events.account";
  public static final String TRANSFER = "msb.events.transfer";
  public static final String SAVING = "msb.events.saving";
  public static final String LENDING = "msb.events.lending";
  public static final String CARD = "msb.events.card";
  public static final String BILL = "msb.events.bill";
  public static final String ONBOARDING = "msb.events.onboarding";
  public static final String MI = "msb.events.mi";

  public static final String[] ALL = {
    ACCOUNT, TRANSFER, SAVING, LENDING, CARD, BILL, ONBOARDING, MI
  };
}
