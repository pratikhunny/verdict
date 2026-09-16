package com.sc.verdict.contract;

import com.sc.verdict.shared.Ids.ConditionId;
import com.sc.verdict.shared.Ids.ContractVersionId;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.MilestoneId;
import com.sc.verdict.shared.Ids.ObligationId;
import com.sc.verdict.shared.Ids.PartyId;
import com.sc.verdict.shared.Ids.PayeeId;
import com.sc.verdict.shared.Money;

import java.time.LocalDate;
import java.util.List;

/**
 * A construction-retention deal, modelled on India's RERA escrow: a homebuyer's funds sit in a
 * ring-fenced pool, and the bank disburses to the developer per construction milestone — each
 * verified by an engineer/architect completion certificate.
 *
 * <p>It is deliberately the opposite of the marketplace deal. Its milestone is <strong>binary</strong>:
 * certified completion is either at or above the milestone threshold (release the whole tranche) or
 * it is not (hold — never pro-rated). It carries an objection condition whose finding is
 * party-approvable, so a disputed milestone routes to HOLD_PENDING_APPROVAL. Same engine, same nodes,
 * a different graph and a different determination profile.
 */
public final class ConstructionDealRegistry implements ContractRegistry {

    public static final DealId DEAL = new DealId("DEAL-RERA-2026-0007");
    public static final ContractVersionId CONTRACT_V1 = new ContractVersionId("CTR-RERA-2026-0007-v1");
    public static final MilestoneId M_STRUCTURE = new MilestoneId("MS-STRUCTURE-COMPLETE");

    public static final PartyId HOMEBUYER = new PartyId("PTY-HOMEBUYER");
    public static final PartyId DEVELOPER = new PartyId("PTY-DEVELOPER");
    public static final PartyId BANK = new PartyId("PTY-SCB-AGENT");
    public static final PartyId AUTHORITY = new PartyId("PTY-RERA-AUTHORITY");
    public static final PayeeId PAYEE_DEVELOPER = new PayeeId("PAYEE-DEVELOPER");

    public static final ObligationId O_STRUCTURE = new ObligationId("R-STRUCTURE");

    /** The escrow pool. Each milestone disburses a tranche; the structure milestone is 40%. */
    public static final Money CONTRACT_VALUE = Money.of("1000000.00", "USD");
    public static final int STRUCTURE_THRESHOLD = 40;

    private final DealDefinition deal = build();

    @Override
    public java.util.Optional<DealDefinition> findDeal(DealId dealId) {
        return dealId.equals(DEAL) ? java.util.Optional.of(deal) : java.util.Optional.empty();
    }

    public DealDefinition constructionDeal() {
        return deal;
    }

    private static DealDefinition build() {
        Milestone structure = new Milestone(M_STRUCTURE, "Structure completed", List.of(
                new Condition(new ConditionId("C-CERT-PRESENT"),
                        Condition.Kind.CERTIFICATE_PRESENT, "cl. 7.1 — engineer completion certificate"),
                new Condition(new ConditionId("C-COMPLETION"),
                        Condition.Kind.COMPLETION_AT_LEAST, "cl. 7.2 — certified completion ≥ 40%", "40"),
                new Condition(new ConditionId("C-NO-OBJECTION"),
                        Condition.Kind.NO_OUTSTANDING_OBJECTION, "cl. 7.4 — no lien or objection")));

        // One payee (the developer), non-severable: a milestone tranche releases whole or not at all.
        Obligation structureTranche = new Obligation(O_STRUCTURE, CONTRACT_V1, M_STRUCTURE, PAYEE_DEVELOPER,
                HOMEBUYER, DEVELOPER, EntitlementRule.fixedPercentage("0.40"),
                false, "cl. 7 — structure milestone tranche", 1, null);

        // Marketplace-shaped fields are unused by this deal's conditions; set sensibly.
        return new DealDefinition(DEAL, CONTRACT_V1, CONTRACT_VALUE, 1,
                "residential project", LocalDate.parse("2027-12-31"), structure, List.of(structureTranche));
    }
}
