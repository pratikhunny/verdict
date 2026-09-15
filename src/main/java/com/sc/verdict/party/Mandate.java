package com.sc.verdict.party;

import com.sc.verdict.shared.Money;
import com.sc.verdict.shared.Ids.RoleId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The authority attached to a {@link DealRole} for one class of instruction.
 *
 * <p>This is the layer that earns its keep. "The buyer's treasury manager may waive discrepancies
 * up to USD 50,000; above that, two signatories" is a mandate — data, editable per deal — not a
 * branch in application code.
 *
 * <p>Mandates are effective-dated for the same reason roles are, and a delegated mandate cannot
 * outlive the mandate it derives from: see
 * {@link AuthorityPolicy} for enforcement of the delegation chain.
 */
public record Mandate(
        RoleId roleId,
        InstructionType instructionType,
        Money ceiling,
        Quorum quorum,
        RoleId delegatedFrom,
        Instant effectiveFrom,
        Instant effectiveTo) {

    public Mandate {
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(instructionType, "instructionType");
        Objects.requireNonNull(quorum, "quorum");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (ceiling != null && ceiling.isNegative()) {
            throw new IllegalArgumentException("ceiling must not be negative");
        }
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
        }
    }

    /** Absent ceiling means unlimited for this instruction type. */
    public Optional<Money> ceilingIfAny() {
        return Optional.ofNullable(ceiling);
    }

    public Optional<RoleId> delegationSource() {
        return Optional.ofNullable(delegatedFrom);
    }

    public boolean isEffectiveAt(Instant at) {
        Objects.requireNonNull(at, "at");
        return !at.isBefore(effectiveFrom) && (effectiveTo == null || at.isBefore(effectiveTo));
    }

    /**
     * Classes of instruction the authority layer recognises.
     *
     * <p>Every state-changing entry point in the system maps to exactly one of these. Adding a new
     * way to move or re-scope money requires adding a constant here, which forces the mandate
     * question to be answered at design time rather than discovered in production.
     */
    public enum InstructionType {
        /** Pay funds into the ring-fenced escrow balance. */
        FUND,
        /** Release earmarked funds to the obligee. */
        RELEASE,
        /** Return funds to the funding party. */
        REFUND,
        /** Accept a documentary discrepancy and permit release notwithstanding. */
        WAIVE_DISCREPANCY,
        /** Alter an obligation's terms — creates a new obligation version. */
        AMEND_OBLIGATION,
        /** Terminate the deal and unwind remaining balances. */
        CANCEL_DEAL
    }

    /** How many distinct authorised signatories must concur. */
    public sealed interface Quorum {

        int required();

        static Quorum single() { return new Single(); }
        static Quorum dual() { return new NofM(2); }
        static Quorum nOfM(int n) { return new NofM(n); }

        record Single() implements Quorum {
            @Override public int required() { return 1; }
        }

        record NofM(int required) implements Quorum {
            public NofM {
                if (required < 2) {
                    throw new IllegalArgumentException("NofM quorum requires at least 2; use Single()");
                }
            }
        }
    }
}
