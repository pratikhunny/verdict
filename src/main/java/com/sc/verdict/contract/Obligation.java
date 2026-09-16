package com.sc.verdict.contract;

import com.sc.verdict.shared.Ids.ContractVersionId;
import com.sc.verdict.shared.Ids.MilestoneId;
import com.sc.verdict.shared.Ids.ObligationId;
import com.sc.verdict.shared.Ids.PartyId;
import com.sc.verdict.shared.Ids.PayeeId;
import com.sc.verdict.shared.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * An entitlement: what one payee is owed when a milestone is met, and under what rule.
 *
 * <p>The obligation is the primitive of the decision plane (ADR-003). It is versioned because
 * amendments create a new version rather than mutating in place — a determination pins the
 * obligation version it examined, so a replay resolves against the terms that applied then.
 *
 * <p>{@code severable} is what makes partial release deterministic rather than a judgement call: a
 * severable obligation under a pro-rata rule releases in proportion to evidenced performance and
 * re-earmarks the remainder as a {@code residualOf} obligation; a non-severable one is all-or-hold.
 */
public record Obligation(
        ObligationId id,
        ContractVersionId contractVersionId,
        MilestoneId milestoneId,
        PayeeId payeeId,
        PartyId payerParty,
        PartyId payeeParty,
        EntitlementRule entitlementRule,
        boolean severable,
        String clauseReference,
        int version,
        ObligationId residualOf) {

    public Obligation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contractVersionId, "contractVersionId");
        Objects.requireNonNull(milestoneId, "milestoneId");
        Objects.requireNonNull(payeeId, "payeeId");
        Objects.requireNonNull(payerParty, "payerParty");
        Objects.requireNonNull(payeeParty, "payeeParty");
        Objects.requireNonNull(entitlementRule, "entitlementRule");
        Objects.requireNonNull(clauseReference, "clauseReference");
        if (version < 1) {
            throw new IllegalArgumentException("obligation version starts at 1");
        }
    }

    /** The entitlement at full performance, before any pro-rating for partial evidence. */
    public Money fullEntitlement(Money contractValue) {
        return entitlementRule.fullEntitlement(contractValue);
    }

    /** Present when this obligation is the retained residual of a partial release. */
    public Optional<ObligationId> residualOfObligation() {
        return Optional.ofNullable(residualOf);
    }

    /** Derive the residual obligation that retains the un-released remainder of a partial release. */
    public Obligation asResidual(EntitlementRule residualRule) {
        return new Obligation(
                new ObligationId(id.value() + "-R"),
                contractVersionId, milestoneId, payeeId, payerParty, payeeParty,
                residualRule, severable, clauseReference, version, id);
    }
}
