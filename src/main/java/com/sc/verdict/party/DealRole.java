package com.sc.verdict.party;

import com.sc.verdict.shared.Money;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.PartyId;
import com.sc.verdict.shared.Ids.RoleId;

import java.time.Instant;
import java.util.Objects;

/**
 * The capacity in which a party participates in one specific deal.
 *
 * <p>Roles are effective-dated because parties are replaced mid-deal by novation, assignment, or
 * insolvency. An instruction is authorised against the role that held as at the instruction's
 * effective instant — never against the role that holds today. Without this, a replay of a release
 * from six months ago produces a different answer, which defeats the point of the decision journal.
 *
 * <p>One party may hold several roles in the same deal: a buyer is obligor on the advance
 * obligation and obligee on the refund path. Roles are therefore not unique per (deal, party).
 */
public record DealRole(
        RoleId id,
        DealId dealId,
        PartyId partyId,
        RoleType role,
        Instant effectiveFrom,
        Instant effectiveTo) {

    public DealRole {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dealId, "dealId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
        }
    }

    /** Half-open interval {@code [effectiveFrom, effectiveTo)}; open-ended when {@code effectiveTo} is null. */
    public boolean isEffectiveAt(Instant at) {
        Objects.requireNonNull(at, "at");
        return !at.isBefore(effectiveFrom) && (effectiveTo == null || at.isBefore(effectiveTo));
    }

    public enum RoleType {
        /** Owes performance or payment under an obligation. */
        OBLIGOR,
        /** Entitled to receive under an obligation. */
        OBLIGEE,
        /** The bank holding and controlling the ring-fenced funds. */
        ESCROW_AGENT,
        /** Empowered to determine a contested disposition, e.g. inspection agency or court. */
        ADJUDICATOR,
        /** Read-only visibility; may not instruct. Typically the marketplace operator. */
        OBSERVER
    }
}
