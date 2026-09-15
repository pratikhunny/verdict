package com.sc.verdict.party;

import com.sc.verdict.shared.Money;
import com.sc.verdict.shared.Ids.RoleId;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of presenting an {@link Instruction} to {@link AuthorityPolicy}.
 *
 * <p>Sealed so that callers must handle every outcome. In particular
 * {@link RequiresCountersignature} is not a failure and must not be collapsed into
 * {@link Rejected}: a dual-control instruction awaiting its second signature is a live workflow
 * item, and treating it as a rejection silently drops legitimate client instructions.
 */
public sealed interface AdmissionDecision {

    /** Authorised. Carries the resolved role so downstream layers need not re-resolve it. */
    record Admitted(RoleId resolvedRole, String rationale) implements AdmissionDecision {
        public Admitted {
            Objects.requireNonNull(resolvedRole, "resolvedRole");
        }
    }

    /** Well-formed and authorised in principle, but short of the mandated quorum. */
    record RequiresCountersignature(RoleId resolvedRole, int required, int obtained)
            implements AdmissionDecision {
        public RequiresCountersignature {
            Objects.requireNonNull(resolvedRole, "resolvedRole");
            if (obtained >= required) {
                throw new IllegalArgumentException("not a quorum shortfall: obtained >= required");
            }
        }
    }

    /** Refused. The code is the audit artefact; the detail is for the operator. */
    record Rejected(Reason reason, String detail) implements AdmissionDecision {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    default boolean isAdmitted() {
        return this instanceof Admitted;
    }

    /**
     * Stable rejection codes. These are written to the decision journal and surfaced to
     * counterparties, so they are part of the contract and must not be renumbered or reworded
     * once a deal has referenced them.
     */
    enum Reason {
        /** No such party on the platform as at the effective instant. */
        PARTY_UNKNOWN("AUT-001"),
        /** Party exists but is DRAFT, SUSPENDED or EXITED as at the effective instant. */
        PARTY_NOT_ACTIVE("AUT-002"),
        /** Sanctions or adverse-media screening did not return a clear result. */
        SCREENING_NOT_CLEAR("AUT-003"),
        /** Party held no role in this deal as at the effective instant. */
        NO_ROLE_IN_DEAL("AUT-004"),
        /** Party held a role, but none carrying a mandate for this instruction type. */
        NO_MANDATE_FOR_INSTRUCTION("AUT-005"),
        /** Instruction amount exceeds the mandate ceiling. */
        CEILING_EXCEEDED("AUT-006"),
        /** Instruction amount is denominated differently from the mandate ceiling. */
        CURRENCY_MISMATCH("AUT-007"),
        /** Acting signatory is not bound to the acting party, or not effective at the instant. */
        SIGNATORY_NOT_BOUND("AUT-008"),
        /** A corporate party instructed without naming a signatory. */
        SIGNATORY_REQUIRED("AUT-009"),
        /** The mandate this authority was delegated from has lapsed or was revoked. */
        DELEGATION_CHAIN_BROKEN("AUT-010"),
        /** Observers and other read-only roles may not instruct. */
        ROLE_MAY_NOT_INSTRUCT("AUT-011");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    static AdmissionDecision reject(Reason reason, String detail) {
        return new Rejected(reason, detail);
    }

    /** Roles that exist for visibility only and can never carry an instructing mandate. */
    List<DealRole.RoleType> NON_INSTRUCTING_ROLES = List.of(DealRole.RoleType.OBSERVER);
}
