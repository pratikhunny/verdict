package com.sc.verdict.app;

import com.sc.verdict.contract.ConstructionDealRegistry;
import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.contract.HeroDealRegistry;
import com.sc.verdict.contract.Obligation;
import com.sc.verdict.examination.ToleranceProfile;
import com.sc.verdict.shared.Money;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The deal types the platform runs — the master agreements. Each entry pairs a
 * {@link DealDefinition} (contract + obligations) with the display metadata and the funding amount
 * its transactions collect. Selecting a deal type drives the whole UI from here: same node library,
 * different contract, different graph, different determination profile.
 */
@Component
public class DealCatalog {

    public static final String MARKETPLACE = "Marketplace trade";
    public static final String CONSTRUCTION = "Construction retention";

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public DealCatalog() {
        DealDefinition marketplace = new HeroDealRegistry().heroDeal();
        entries.put(MARKETPLACE, new Entry(MARKETPLACE, marketplace,
                trancheValue(marketplace),  // fund the shipment tranche (600k), fully earmarked
                List.of(
                    new Views.PartyView("Selangor Components Sdn Bhd", "Payer / Obligor", "MY", "Buyer — funds & approves"),
                    new Views.PartyView("Hanoi Precision Trading JSC", "Payee / Obligee", "VN", "Supplier — submits, gets paid"),
                    new Views.PartyView("Standard Chartered Bank", "Escrow Agent", "SG", "Holds funds, executes"),
                    new Views.PartyView("Marketplace Operator", "Observer", "SG", "Read-only — cannot instruct")),
                List.of(
                    "Buyer · Treasury Manager — waive discrepancies ≤ USD 50,000 (single signature)",
                    "Buyer · CFO — dual signature, uncapped",
                    "Bank · Escrow Operations — release / refund (single)"),
                List.of(
                    new Views.ChipDto("Goods", marketplace.contractedQuantity() + " units · " + marketplace.goodsDescription()),
                    new Views.ChipDto("Proof", "eBL + invoice + packing list"),
                    new Views.ChipDto("Latest shipment", marketplace.latestShipmentDate().toString()),
                    new Views.ChipDto("Release rule", "pro-rata to evidenced quantity (severable)"))));

        DealDefinition construction = new ConstructionDealRegistry().constructionDeal();
        entries.put(CONSTRUCTION, new Entry(CONSTRUCTION, construction,
                construction.contractValue(),  // fund the full RERA pool (1M); the milestone earmarks 40%
                List.of(
                    new Views.PartyView("Priya & Arjun Nair (homebuyers)", "Payer / Obligor", "IN", "Buyers — fund the RERA escrow"),
                    new Views.PartyView("Marina Heights Developers Pvt Ltd", "Payee / Obligee", "IN", "Developer — paid per milestone"),
                    new Views.PartyView("Standard Chartered Bank", "Escrow Agent", "IN", "Holds the pool, disburses on proof"),
                    new Views.PartyView("RERA Authority", "Adjudicator", "IN", "Resolves objections / liens")),
                List.of(
                    "Homebuyer — approve release notwithstanding an objection (dual signature)",
                    "Bank · Escrow Operations — disburse milestone tranche (single)",
                    "RERA Authority — adjudicate a contested milestone"),
                List.of(
                    new Views.ChipDto("Milestone", "Structure · 40% tranche"),
                    new Views.ChipDto("Proof", "engineer completion certificate"),
                    new Views.ChipDto("Release rule", "binary — certified completion ≥ 40% (not pro-rata)"))));
    }

    public List<String> dealTypes() {
        return List.copyOf(entries.keySet());
    }

    public boolean has(String dealType) {
        return entries.containsKey(dealType);
    }

    public Entry entry(String dealType) {
        Entry e = entries.get(dealType);
        if (e == null) {
            throw new IllegalArgumentException("no such deal type: " + dealType);
        }
        return e;
    }

    public DealDefinition deal(String dealType) {
        return entry(dealType).deal();
    }

    public Money fundAmount(String dealType) {
        return entry(dealType).fundAmount();
    }

    public Views.ContractView contractView(String dealType) {
        Entry e = entry(dealType);
        DealDefinition d = e.deal();
        List<Views.ObligationDto> payees = examinedObligations(d).stream()
                .map(o -> new Views.ObligationDto(o.id().value(), o.payeeId().value(), o.payeeParty().value(),
                        o.entitlementRule().describe(), o.severable(), o.clauseReference(),
                        Mapper.money(o.fullEntitlement(d.contractValue()))))
                .toList();
        List<Views.ConditionDto> conditions = d.shipmentMilestone().conditions().stream()
                .map(c -> new Views.ConditionDto(c.kind().name(), c.clauseReference()))
                .toList();
        return new Views.ContractView(d.dealId().value(), e.name(), Mapper.money(trancheValue(d)),
                e.chips(), ToleranceProfile.demoDefault().ruleReference(),
                e.parties(), e.mandate(), conditions, payees);
    }

    static List<Obligation> examinedObligations(DealDefinition d) {
        return d.obligationsFor(d.shipmentMilestone().id());
    }

    private static Money trancheValue(DealDefinition d) {
        Money v = Money.zero(d.contractValue().currency());
        for (Obligation o : examinedObligations(d)) {
            v = v.plus(o.fullEntitlement(d.contractValue()));
        }
        return v;
    }

    public record Entry(String name, DealDefinition deal, Money fundAmount,
                        List<Views.PartyView> parties, List<String> mandate, List<Views.ChipDto> chips) {}
}
