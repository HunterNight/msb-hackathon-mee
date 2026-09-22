package com.app.service.tool;

import com.app.constant.ToolCodes;
import com.app.dto.internal.MiInternal.ToolResult;
import com.app.service.client.PeerClients.AccountClient;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Read-only views of the customer's money, for the general agent. */
public final class AccountTools {

  private AccountTools() {}

  @Component
  public static class GetSpendingSummary implements MiTool {

    private final AccountClient accounts;

    public GetSpendingSummary(AccountClient accounts) {
      this.accounts = accounts;
    }

    @Override
    public String code() {
      return ToolCodes.GET_SPENDING_SUMMARY;
    }

    @Override
    public boolean mutating() {
      return false;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      Object month = args.get("month");
      var summary =
          accounts.spending(context.customerId(), month == null ? null : month.toString()).data();
      return new ToolResult(
          code(), true, summary, null, (System.nanoTime() - started) / 1_000_000);
    }
  }

  @Component
  public static class GetAccountSummary implements MiTool {

    private final AccountClient accounts;

    public GetAccountSummary(AccountClient accounts) {
      this.accounts = accounts;
    }

    @Override
    public String code() {
      return ToolCodes.GET_ACCOUNT_SUMMARY;
    }

    @Override
    public boolean mutating() {
      return false;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      long started = System.nanoTime();
      return new ToolResult(
          code(),
          true,
          accounts.accounts(context.customerId()).data(),
          null,
          (System.nanoTime() - started) / 1_000_000);
    }
  }

  @Component
  public static class SetBudget implements MiTool {

    private final ProposalTools proposalTools;

    public SetBudget(ProposalTools proposalTools) {
      this.proposalTools = proposalTools;
    }

    @Override
    public String code() {
      return ToolCodes.SET_BUDGET;
    }

    /** Mutating in the CMS sense — it changes state — even though no money moves. */
    @Override
    public boolean mutating() {
      return true;
    }

    @Override
    public ToolResult execute(Context context, Map<String, Object> args) {
      return proposalTools.setBudget(context, args);
    }
  }
}
