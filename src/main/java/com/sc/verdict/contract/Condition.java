package com.sc.verdict.contract;

import com.sc.verdict.shared.Ids.ConditionId;

import java.util.Objects;

/**
 * One testable condition attached to a milestone.
 *
 * <p>A condition names <em>what</em> must be true and <em>which clause</em> requires it; it does not
 * say how to test it. The {@link Kind} is the contract between the contract model and the
 * examination engine: the engine has one evaluator per kind, and a deal that needs a new kind of
 * check adds a constant here and an evaluator there — it does not add a branch to a monolith.
 *
 * <p>The clause reference is carried so that a finding can cite the exact clause it breaches, which
 * is the difference between "quantity mismatch" and a findings notice an ops officer can defend.
 */
public record Condition(ConditionId id, Kind kind, String clauseReference, String param) {

    public Condition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(clauseReference, "clauseReference");
    }

    /** Most conditions carry no parameter; the threshold ones (e.g. COMPLETION_AT_LEAST) do. */
    public Condition(ConditionId id, Kind kind, String clauseReference) {
        this(id, kind, clauseReference, null);
    }

    /**
     * The classes of check the examination engine understands. Each maps to exactly one extracted
     * fact and one evaluator; adding a kind forces both to be written rather than discovered.
     */
    public enum Kind {
        /** A required document is present in the submission (e.g. the eBL). */
        DOCUMENT_PRESENT,
        /** The goods description on the evidence matches the contract, within tolerance. */
        GOODS_DESCRIPTION_MATCHES,
        /** The evidenced quantity matches the contracted quantity. */
        QUANTITY_MATCHES,
        /** The shipment date is on or before the contract's latest shipment date. */
        SHIPPED_WITHIN_LATEST_DATE,

        // ---- construction retention (RERA) ----

        /** An engineer/architect completion certificate is present. */
        CERTIFICATE_PRESENT,
        /** Certified completion is at least the milestone threshold (in {@link Condition#param()}, a %). */
        COMPLETION_AT_LEAST,
        /** No outstanding objection or lien is recorded against the milestone. */
        NO_OUTSTANDING_OBJECTION
    }
}
