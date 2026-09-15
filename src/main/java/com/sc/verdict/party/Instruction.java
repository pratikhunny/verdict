package com.sc.verdict.party;

import com.sc.verdict.shared.Money;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.InstructionId;
import com.sc.verdict.shared.Ids.PartyId;
import com.sc.verdict.shared.Ids.SignatoryId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A request to change state, presented to {@link AuthorityPolicy} before any other layer acts.
 *
 * <p>{@code effectiveAt} is the instant against which all temporal resolution happens — party
 * status, role validity, mandate validity. It is supplied rather than read from the clock so that
 * a decision can be replayed years later and produce the identical admission outcome.
 *
 * <p>{@code instructionId} is the idempotency key. The authority layer does not itself deduplicate;
 * it carries the key so that downstream ledger and settlement layers can.
 */
public record Instruction(
        InstructionId instructionId,
        DealId dealId,
        PartyId actingParty,
        SignatoryId actingSignatory,
        Mandate.InstructionType type,
        Money amount,
        List<SignatoryId> countersignatures,
        Instant effectiveAt) {

    public Instruction {
        Objects.requireNonNull(instructionId, "instructionId");
        Objects.requireNonNull(dealId, "dealId");
        Objects.requireNonNull(actingParty, "actingParty");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        if (amount != null && amount.isNegative()) {
            throw new IllegalArgumentException("instruction amount must not be negative");
        }
        countersignatures = List.copyOf(countersignatures == null ? List.of() : countersignatures);
    }

    /**
     * Amounts are absent for instruction types that do not themselves move money
     * (for example {@link Mandate.InstructionType#CANCEL_DEAL}). A mandate ceiling cannot be
     * evaluated against an absent amount, which the policy treats as requiring an unlimited mandate.
     */
    public Optional<Money> amountIfAny() {
        return Optional.ofNullable(amount);
    }

    public Optional<SignatoryId> actingSignatoryIfAny() {
        return Optional.ofNullable(actingSignatory);
    }

    /**
     * Distinct concurring signatories, counting the acting signatory once.
     *
     * <p>Deduplication is the four-eyes control: one person signing twice, or countersigning their
     * own instruction, must not satisfy a dual-control mandate.
     */
    public Set<SignatoryId> distinctSigners() {
        var signers = new java.util.LinkedHashSet<SignatoryId>();
        actingSignatoryIfAny().ifPresent(signers::add);
        signers.addAll(countersignatures);
        return Set.copyOf(signers);
    }

    /** A natural person authorised to act for a corporate {@link Party}. */
    public record Signatory(
            SignatoryId id,
            PartyId partyId,
            String fullName,
            Instant effectiveFrom,
            Instant effectiveTo) {

        public Signatory {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(partyId, "partyId");
            Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        }

        public boolean isEffectiveAt(Instant at) {
            return !at.isBefore(effectiveFrom) && (effectiveTo == null || at.isBefore(effectiveTo));
        }
    }
}
