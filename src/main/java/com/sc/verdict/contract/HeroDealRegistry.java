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
import java.util.Optional;

/**
 * The hero deal, DEAL-2026-0417, as a registry fixture: a cross-border marketplace trade, buyer in
 * Malaysia, supplier in Vietnam, SCB as agent. USD 1,000,000 for 1,000 units of tablet computers,
 * latest shipment 2026-09-10.
 *
 * <p>The shipment milestone (O2) is the one the four evidence packs examine, and it is deliberately
 * the multi-payee tranche: supplier 96%, marketplace commission 3.3%, bank fee 0.7% of the 60%
 * shipment tranche — three obligations, one milestone, all severable and pro-rated together. The
 * advance (O1) and retention (O3) obligations are present so the reconciliation control sums over a
 * complete deal.
 */
public final class HeroDealRegistry implements ContractRegistry {

    public static final DealId DEAL = new DealId("DEAL-2026-0417");
    public static final ContractVersionId CONTRACT_V1 = new ContractVersionId("CTR-2026-0417-v1");

    public static final MilestoneId M_ORDER = new MilestoneId("MS-ORDER-ACCEPTED");
    public static final MilestoneId M_SHIPMENT = new MilestoneId("MS-SHIPMENT-EVIDENCED");
    public static final MilestoneId M_ACCEPTANCE = new MilestoneId("MS-GOODS-ACCEPTED");

    public static final PartyId BUYER = new PartyId("PTY-BUYER-MY");
    public static final PartyId SUPPLIER = new PartyId("PTY-SELLER-VN");
    public static final PartyId MARKETPLACE = new PartyId("PTY-MARKETPLACE");
    public static final PartyId BANK = new PartyId("PTY-SCB-AGENT");

    public static final PayeeId PAYEE_SUPPLIER = new PayeeId("PAYEE-SUPPLIER");
    public static final PayeeId PAYEE_MARKETPLACE = new PayeeId("PAYEE-MARKETPLACE");
    public static final PayeeId PAYEE_BANK = new PayeeId("PAYEE-BANK-FEE");

    public static final ObligationId O1 = new ObligationId("O1");
    public static final ObligationId O2A = new ObligationId("O2a");
    public static final ObligationId O2B = new ObligationId("O2b");
    public static final ObligationId O2C = new ObligationId("O2c");
    public static final ObligationId O3 = new ObligationId("O3");

    public static final Money CONTRACT_VALUE = Money.of("1000000.00", "USD");
    public static final long CONTRACTED_QUANTITY = 1000L;
    public static final String GOODS = "tablet computers";
    public static final LocalDate LATEST_SHIPMENT = LocalDate.parse("2026-09-10");

    private final DealDefinition deal = build();

    @Override
    public Optional<DealDefinition> findDeal(DealId dealId) {
        return dealId.equals(DEAL) ? Optional.of(deal) : Optional.empty();
    }

    /** Convenience for fixtures and the demo harness. */
    public DealDefinition heroDeal() {
        return deal;
    }

    private static DealDefinition build() {
        Milestone shipment = new Milestone(M_SHIPMENT, "Shipment evidenced", List.of(
                new Condition(new ConditionId("C-EBL-PRESENT"),
                        Condition.Kind.DOCUMENT_PRESENT, "cl. 4.1 — transport document"),
                new Condition(new ConditionId("C-GOODS-MATCH"),
                        Condition.Kind.GOODS_DESCRIPTION_MATCHES, "cl. 4.3 — goods description"),
                new Condition(new ConditionId("C-QTY-MATCH"),
                        Condition.Kind.QUANTITY_MATCHES, "cl. 4.2 — quantity"),
                new Condition(new ConditionId("C-SHIP-DATE"),
                        Condition.Kind.SHIPPED_WITHIN_LATEST_DATE, "cl. 4.4 — latest shipment date")));

        // O2 shipment tranche = 60% of contract value, split across three payees, all severable.
        Obligation o2a = new Obligation(O2A, CONTRACT_V1, M_SHIPMENT, PAYEE_SUPPLIER,
                BUYER, SUPPLIER, EntitlementRule.shareOfTranche("0.60", "0.96"),
                true, "cl. 4 — shipment tranche (supplier)", 1, null);
        Obligation o2b = new Obligation(O2B, CONTRACT_V1, M_SHIPMENT, PAYEE_MARKETPLACE,
                BUYER, MARKETPLACE, EntitlementRule.shareOfTranche("0.60", "0.033"),
                true, "cl. 4 — shipment tranche (marketplace commission)", 1, null);
        Obligation o2c = new Obligation(O2C, CONTRACT_V1, M_SHIPMENT, PAYEE_BANK,
                BUYER, BANK, EntitlementRule.shareOfTranche("0.60", "0.007"),
                true, "cl. 4 — shipment tranche (bank fee)", 1, null);

        // O1 advance and O3 retention: single-payee, non-severable, present for reconciliation.
        Obligation o1 = new Obligation(O1, CONTRACT_V1, M_ORDER, PAYEE_SUPPLIER,
                BUYER, SUPPLIER, EntitlementRule.fixedPercentage("0.20"),
                false, "cl. 3 — advance", 1, null);
        Obligation o3 = new Obligation(O3, CONTRACT_V1, M_ACCEPTANCE, PAYEE_SUPPLIER,
                BUYER, SUPPLIER, EntitlementRule.fixedPercentage("0.20"),
                false, "cl. 5 — retention", 1, null);

        return new DealDefinition(DEAL, CONTRACT_V1, CONTRACT_VALUE, CONTRACTED_QUANTITY,
                GOODS, LATEST_SHIPMENT, shipment, List.of(o1, o2a, o2b, o2c, o3));
    }
}
