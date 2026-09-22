package com.app.constant;

/** Paths and scopes. Internal only — the gateway has no route to this service (design §6). */
public final class Routes {

  private Routes() {}

  public static final String MEMORY = AppConstants.INTERNAL + "/memory";
  public static final String CUSTOMER = MEMORY + "/{customerId}";
  public static final String RECALL = CUSTOMER + "/recall";
  public static final String SNAPSHOT = CUSTOMER + "/snapshot";
  public static final String RECORDS = CUSTOMER + "/records";
  public static final String RECORD = RECORDS + "/{id}";
  public static final String CANDIDATES = CUSTOMER + "/candidates";
  public static final String CONFIRM = RECORD + "/confirm";
  public static final String REJECT = RECORD + "/reject";
  public static final String DEACTIVATE = RECORD + "/deactivate";
  public static final String FORGET = RECORD + "/forget";
  public static final String HYPOTHESES = CUSTOMER + "/hypotheses";
  public static final String GRAPH = CUSTOMER + "/graph";
  public static final String WHY = RECORD + "/why";
  public static final String CONSENT = CUSTOMER + "/consent";
  public static final String EXPORT = CUSTOMER + "/export";
  public static final String ERASURES = CUSTOMER + "/erasures";
  public static final String ERASURE = ERASURES + "/{id}";

  public static final String ADMIN = "/admin";
  public static final String ADMIN_REPLAY = ADMIN + "/replay";
  public static final String ADMIN_DLQ = ADMIN + "/dlq";
  public static final String ADMIN_DLQ_RETRY = ADMIN_DLQ + "/{id}/retry";
  public static final String ADMIN_DLQ_ITEM = ADMIN_DLQ + "/{id}";
  public static final String ADMIN_POISONING = ADMIN + "/metrics/poisoning";
  public static final String ADMIN_KEYS_ROTATE = ADMIN + "/keys/rotate";
  public static final String ADMIN_AUDIT = ADMIN + "/audit";

  public static final String SCOPE_READ = "SCOPE_memory:read";
  public static final String SCOPE_WRITE_CHAT = "SCOPE_memory:write:chat";
  public static final String SCOPE_ERASE = "SCOPE_memory:erase";
  public static final String SCOPE_ADMIN = "SCOPE_memory:admin";
}
