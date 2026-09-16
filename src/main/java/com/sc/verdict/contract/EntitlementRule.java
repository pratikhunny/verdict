package com.sc.verdict.contract;

import com.sc.verdict.shared.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * How an obligation's <em>full</em> entitlement is computed from the deal's contract value.
 *
 * <p>This is the entitlement at full performance — the ceiling. Pro-rating for partial performance
 * is a separate step applied by the examination engine using the obligation's severability, not a
 * property of the rule: the rule answers "what is owed when the milestone is fully met", and the
 * engine answers "how much of that is released on this evidence".
 *
 * <p>Rules are pure functions of the contract value. They read no clock and hold no state, which is
 * what lets a determination replay to the identical amount.
 */
public sealed interface EntitlementRule {

    /** The entitlement owed when the milestone is fully met. */
    Money fullEntitlement(Money contractValue);

    /** A human-readable form for the findings notice and journal. */
    String describe();

    static EntitlementRule fixedPercentage(String fraction) {
        return new FixedPercentage(new BigDecimal(fraction));
    }

    static EntitlementRule shareOfTranche(String tranchePercent, String payeeShare) {
        return new ShareOfTranche(new BigDecimal(tranchePercent), new BigDecimal(payeeShare));
    }

    /** A flat percentage of contract value, e.g. the 20% advance. */
    record FixedPercentage(BigDecimal fraction) implements EntitlementRule {
        public FixedPercentage {
            Objects.requireNonNull(fraction, "fraction");
        }

        @Override
        public Money fullEntitlement(Money contractValue) {
            return contractValue.multiply(fraction);
        }

        @Override
        public String describe() {
            return fraction.movePointRight(2).stripTrailingZeros().toPlainString() + "% of contract value";
        }
    }

    /**
     * A payee's share of a milestone tranche, e.g. the supplier's 96% of the 60% shipment tranche.
     * Two factors because the modelling point of the product is that one milestone's tranche is
     * split across several payees — supplier, marketplace commission, bank fee — that scale together
     * under partial release.
     */
    record ShareOfTranche(BigDecimal tranchePercent, BigDecimal payeeShare) implements EntitlementRule {
        public ShareOfTranche {
            Objects.requireNonNull(tranchePercent, "tranchePercent");
            Objects.requireNonNull(payeeShare, "payeeShare");
        }

        @Override
        public Money fullEntitlement(Money contractValue) {
            return contractValue.multiply(tranchePercent).multiply(payeeShare);
        }

        @Override
        public String describe() {
            return payeeShare.movePointRight(2).stripTrailingZeros().toPlainString()
                    + "% of the "
                    + tranchePercent.movePointRight(2).stripTrailingZeros().toPlainString()
                    + "% shipment tranche";
        }
    }
}
