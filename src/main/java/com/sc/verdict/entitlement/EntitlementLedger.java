package com.sc.verdict.entitlement;

import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.contract.Obligation;
import com.sc.verdict.examination.Determination;
import com.sc.verdict.examination.DisbursementLine;
import com.sc.verdict.examination.Outcome;
import com.sc.verdict.examination.ResidualLine;
import com.sc.verdict.shared.Ids.ObligationId;
import com.sc.verdict.shared.Money;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The decision plane's independent view of what is still owed: outstanding entitlement per obligation.
 *
 * <p>This exists so the reconciliation control has two numbers to compare that were computed on
 * different sides of the system (ADR-005). This ledger reduces entitlement from the determination —
 * the decision-plane source of truth — while the money-plane {@link com.sc.verdict.ledger.FundControlLedger}
 * reduces earmarks from its own postings. If the money plane ever mis-posts, the two drift and the
 * control catches it. It imports no money-plane type.
 */
public final class EntitlementLedger {

    private final Map<ObligationId, Money> outstanding = new LinkedHashMap<>();

    /** Seed each obligation with its full entitlement at full performance. */
    public void seedFromDeal(DealDefinition deal) {
        for (Obligation o : deal.obligations()) {
            outstanding.put(o.id(), o.fullEntitlement(deal.contractValue()));
        }
    }

    /** Seed a single obligation's entitlement — used when a transaction funds only part of a deal. */
    public void seed(com.sc.verdict.shared.Ids.ObligationId obligationId, Money full) {
        outstanding.put(obligationId, full);
    }

    /** Reduce entitlement to reflect a determination, independently of any money movement. */
    public void apply(Determination determination) {
        Objects.requireNonNull(determination, "determination");
        if (determination.outcome() == Outcome.HOLD || determination.outcome() == Outcome.HOLD_PENDING_APPROVAL) {
            return; // still fully owed
        }
        // Released obligations are discharged; residuals carry forward what stays owed.
        for (DisbursementLine line : determination.disbursements()) {
            outstanding.put(line.obligation(), Money.zero(line.amount().currency()));
        }
        for (ResidualLine line : determination.residuals()) {
            outstanding.put(line.residualObligation(), line.retained());
        }
    }

    public Money totalOutstanding(java.util.Currency currency) {
        Money total = Money.zero(currency);
        for (Money m : outstanding.values()) {
            if (!m.isZero()) {
                total = total.plus(m);
            }
        }
        return total;
    }

    /** Outstanding entitlement per obligation, excluding discharged ones. */
    public Map<ObligationId, Money> outstandingByObligation() {
        var out = new LinkedHashMap<ObligationId, Money>();
        outstanding.forEach((id, m) -> {
            if (!m.isZero()) {
                out.put(id, m);
            }
        });
        return out;
    }
}
