package com.app.constant;

import java.math.BigDecimal;

/** Regexes, lengths and numeric bounds used by Bean Validation annotations (guideline 01 §3). */
public final class ValidationConstants {

  private ValidationConstants() {}

  /** Vietnamese mobile number, local or +84 form. */
  public static final String PHONE_REGEX = "^(0|\\+84)(3|5|7|8|9)[0-9]{8}$";

  /** MSB account numbers are 14 digits (screen 27 inline-error rule). */
  public static final String ACCOUNT_NUMBER_REGEX = "^[0-9]{14}$";

  public static final String BANK_CODE_REGEX = "^[A-Z]{3,10}$";
  public static final String NATIONAL_ID_REGEX = "^[0-9]{12}$";
  public static final String OTP_REGEX = "^[0-9]{6}$";
  public static final String PIN_REGEX = "^[0-9]{6}$";
  public static final String REF_REGEX = "^[A-Z0-9]{6,24}$";
  public static final String LOCALE_REGEX = "^(vi|en)$";
  public static final String CURRENCY_REGEX = "^VND$";

  public static final int NAME_MAX = 120;
  public static final int NOTE_MAX = 210;
  public static final int SHORT_TEXT_MAX = 64;
  public static final int LONG_TEXT_MAX = 2000;
  public static final int MESSAGE_MAX = 1000;

  public static final BigDecimal AMOUNT_MIN = new BigDecimal("1000");
  public static final BigDecimal AMOUNT_MAX = new BigDecimal("500000000");

  public static final String AMOUNT_MIN_LITERAL = "1000";
  public static final String AMOUNT_MAX_LITERAL = "500000000";
}
