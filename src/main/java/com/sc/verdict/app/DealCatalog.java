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
                    new Views.PartyView("Selangor Components Sdn Bhd", "Corporate", "Malaysia", "CLEAR"),
                    new Views.PartyView("Hanoi Precision Trading JSC", "Corporate", "Vietnam", "CLEAR"),
                    new Views.PartyView("Standard Chartered Bank", "Bank (agent)", "Singapore", "CLEAR"),
                    new Views.PartyView("Proxtera Marketplace", "Corporate", "Singapore", "CLEAR")),
                List.of(
                    new Views.ResponsibilityView("Selangor Components Sdn Bhd", "Payer / Obligor",
                        "Treasury Manager — waive ≤ USD 50k (single); CFO — dual, uncapped"),
                    new Views.ResponsibilityView("Hanoi Precision Trading JSC", "Payee / Obligee",
                        "Submits shipment evidence; receives on release"),
                    new Views.ResponsibilityView("Standard Chartered Bank", "Escrow Agent",
                        "Release / refund (single signature)"),
                    new Views.ResponsibilityView("Proxtera Marketplace", "Observer",
                        "Read-only — cannot instruct")),
                List.of( // ② template chips — type & rules only, no quantity/date/amount
                    new Views.ChipDto("Goods type", marketplace.goodsDescription()),
                    new Views.ChipDto("Proof required", "eBL + invoice + packing list"),
                    new Views.ChipDto("Incoterms", "CIF Port Klang"),
                    new Views.ChipDto("Release rule", "pro-rata to evidenced quantity (severable)")),
                List.of( // Ⓐ order terms — per transaction
                    new Views.ChipDto("Order quantity", String.format("%,d units", marketplace.contractedQuantity())),
                    new Views.ChipDto("Latest shipment date", marketplace.latestShipmentDate().toString()))));

        DealDefinition construction = new ConstructionDealRegistry().constructionDeal();
        entries.put(CONSTRUCTION, new Entry(CONSTRUCTION, construction,
                construction.contractValue(),  // fund the full RERA pool (1M); the milestone earmarks 40%
                List.of(
                    new Views.PartyView("Priya & Arjun Nair", "Individuals", "India", "CLEAR"),
                    new Views.PartyView("Marina Heights Developers Pvt Ltd", "Corporate", "India", "CLEAR"),
                    new Views.PartyView("Standard Chartered Bank", "Bank (agent)", "India", "CLEAR"),
                    new Views.PartyView("RERA Authority", "Regulator", "India", "CLEAR")),
                List.of(
                    new Views.ResponsibilityView("Priya & Arjun Nair", "Payer / Obligor (homebuyers)",
                        "Approve release notwithstanding an objection (dual signature)"),
                    new Views.ResponsibilityView("Marina Heights Developers Pvt Ltd", "Payee / Obligee (developer)",
                        "Submits completion certificate; paid per milestone"),
                    new Views.ResponsibilityView("Standard Chartered Bank", "Escrow Agent",
                        "Disburse milestone tranche (single signature)"),
                    new Views.ResponsibilityView("RERA Authority", "Adjudicator",
                        "Adjudicate a contested milestone / objection")),
                List.of( // ② template chips — type & rules only
                    new Views.ChipDto("Milestone", "Structure · 40% tranche"),
                    new Views.ChipDto("Proof required", "engineer completion certificate"),
                    new Views.ChipDto("Release rule", "binary — certified completion ≥ 40% (not pro-rata)")),
                List.of( // Ⓐ order terms — per transaction
                    new Views.ChipDto("Completion required", "≥ 40%"),
                    new Views.ChipDto("Milestone claimed", "Structure"))));
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
                e.parties(), e.responsibilities(), conditions, payees);
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

    public List<Views.ChipDto> orderTerms(String dealType) {
        return entry(dealType).orderTerms();
    }

    public record Entry(String name, DealDefinition deal, Money fundAmount,
                        List<Views.PartyView> parties, List<Views.ResponsibilityView> responsibilities,
                        List<Views.ChipDto> chips, List<Views.ChipDto> orderTerms) {}
}
