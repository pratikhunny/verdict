package com.sc.verdict.recon;

import com.sc.verdict.entitlement.EntitlementLedger;
import com.sc.verdict.ledger.FundControlLedger;
import com.sc.verdict.shared.Money;

import java.util.Currency;

/**
 * The reconciliation control: Σ entitlements (decision plane) = Σ earmarks (money plane).
 *
 * <p>This is the control that justifies the three-plane split (ADR-001, ADR-005). Without it the
 * separation is overhead; with it, the two planes can be independently correct and any drift between
 * "what is owed" and "what is reserved" is caught continuously rather than at month-end. It is run
 * after every determination in the demo, and it must hold every time.
 */
public final class Reconciliation {

    private Reconciliation() {}

    public static Result check(EntitlementLedger entitlements, FundControlLedger funds, Currency currency) {
        Money owed = entitlements.totalOutstanding(currency);
        Money reserved = funds.reservedTotal();
        Money difference = owed.minus(reserved);
        return new Result(owed, reserved, difference, difference.isZero());
    }

    /** The outcome of one reconciliation pass. {@code balanced} is the control; it must be true. */
    public record Result(Money entitlementTotal, Money earmarkTotal, Money difference, boolean balanced) {

        public String describe() {
            return "entitlement %s vs earmark %s → %s"
                    .formatted(entitlementTotal, earmarkTotal, balanced ? "BALANCED" : "DRIFT " + difference);
        }
    }
}
