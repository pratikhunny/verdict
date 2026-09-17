package com.sc.verdict.app;

import com.sc.verdict.contract.Condition;
import com.sc.verdict.contract.ConstructionDealRegistry;
import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.contract.HeroDealRegistry;
import com.sc.verdict.contract.Milestone;
import com.sc.verdict.contract.Obligation;
import com.sc.verdict.examination.ToleranceProfile;
import com.sc.verdict.shared.Money;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The deal types the platform runs — the master agreements. Each entry pairs a template
 * {@link DealDefinition} with display metadata, an order-form spec (the per-transaction inputs an
 * operator supplies), and a builder that turns those inputs into a <em>per-transaction</em>
 * {@link DealDefinition}. Selecting a deal type drives the whole UI; filling the order form drives
 * the engine — the entered quantity, dates, value and thresholds are what the examination compares
 * evidence against, not baked-in constants.
 */
@Component
public class DealCatalog {

    public static final String MARKETPLACE = "Marketplace trade";
    public static final String CONSTRUCTION = "Construction retention";

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public DealCatalog() {
        // ---------- Marketplace trade (hero deal) ----------
        DealDefinition marketplace = new HeroDealRegistry().heroDeal();
        entries.put(MARKETPLACE, new Entry(MARKETPLACE, marketplace,
                false, // fund the shipment tranche (60%), fully earmarked
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
                List.of( // order-form fields — the per-transaction inputs
                    new Views.FieldSpec("orderRef", "Purchase-order reference", "text", "PO-2026-0417", null,
                        "The buyer's PO this escrow order settles against."),
                    new Views.FieldSpec("contractValue", "Order value (total)", "money", "1000000", "USD",
                        "Full contract value; the shipment tranche is 60% of this."),
                    new Views.FieldSpec("quantity", "Order quantity", "number", "1000", "units",
                        "Contracted units — the eBL quantity is checked against this."),
                    new Views.FieldSpec("latestShipmentDate", "Latest shipment date", "date", "2026-09-10", null,
                        "Ship on or before this date, or the finding is a timing breach.")),
                DealCatalog::buildMarketplace,
                DealCatalog::marketplaceOrderTerms));

        // ---------- Construction retention (RERA) ----------
        DealDefinition construction = new ConstructionDealRegistry().constructionDeal();
        entries.put(CONSTRUCTION, new Entry(CONSTRUCTION, construction,
                true, // fund the whole RERA pool; the milestone earmarks its tranche
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
                    new Views.ChipDto("Release rule", "binary — certified completion ≥ threshold (not pro-rata)")),
                List.of( // order-form fields
                    new Views.FieldSpec("projectRef", "Project / unit reference", "text", "MARINA-HTS-T2-1804", null,
                        "The unit whose escrow pool this milestone draws from."),
                    new Views.FieldSpec("poolValue", "Escrow pool (total)", "money", "1000000", "USD",
                        "The homebuyer's ring-fenced funds; the milestone releases a tranche of this."),
                    new Views.FieldSpec("completionThreshold", "Required completion", "number", "40", "%",
                        "Certified completion must reach this to release the tranche — binary, not pro-rata."),
                    new Views.FieldSpec("milestoneLabel", "Milestone claimed", "text", "Structure", null,
                        "The construction milestone the developer is claiming payment for.")),
                DealCatalog::buildConstruction,
                DealCatalog::constructionOrderTerms));
    }

    // ---------------------------------------------------------------- per-transaction builders

    private static DealDefinition buildMarketplace(Map<String, String> in) {
        DealDefinition base = new HeroDealRegistry().heroDeal();
        Money value = Money.of(num(in, "contractValue", "1000000") + ".00", "USD");
        long qty = Long.parseLong(num(in, "quantity", "1000"));
        LocalDate date = LocalDate.parse(v(in, "latestShipmentDate", "2026-09-10"));
        return new DealDefinition(base.dealId(), base.contractVersionId(), value, qty,
                base.goodsDescription(), date, base.shipmentMilestone(), base.obligations());
    }

    private static List<Views.ChipDto> marketplaceOrderTerms(Map<String, String> in) {
        long qty = Long.parseLong(num(in, "quantity", "1000"));
        return List.of(
                new Views.ChipDto("PO reference", v(in, "orderRef", "PO-2026-0417")),
                new Views.ChipDto("Order value", "USD " + fmt(num(in, "contractValue", "1000000"))),
                new Views.ChipDto("Order quantity", String.format("%,d units", qty)),
                new Views.ChipDto("Latest shipment date", v(in, "latestShipmentDate", "2026-09-10")));
    }

    private static DealDefinition buildConstruction(Map<String, String> in) {
        DealDefinition base = new ConstructionDealRegistry().constructionDeal();
        Money pool = Money.of(num(in, "poolValue", "1000000") + ".00", "USD");
        String threshold = num(in, "completionThreshold", "40");
        Milestone src = base.shipmentMilestone();
        List<Condition> conditions = src.conditions().stream()
                .map(c -> c.kind() == Condition.Kind.COMPLETION_AT_LEAST
                        ? new Condition(c.id(), c.kind(), "cl. 7.2 — certified completion ≥ " + threshold + "%", threshold)
                        : c)
                .toList();
        Milestone milestone = new Milestone(src.id(), src.name(), conditions);
        return new DealDefinition(base.dealId(), base.contractVersionId(), pool, 1,
                base.goodsDescription(), base.latestShipmentDate(), milestone, base.obligations());
    }

    private static List<Views.ChipDto> constructionOrderTerms(Map<String, String> in) {
        return List.of(
                new Views.ChipDto("Project reference", v(in, "projectRef", "MARINA-HTS-T2-1804")),
                new Views.ChipDto("Escrow pool", "USD " + fmt(num(in, "poolValue", "1000000"))),
                new Views.ChipDto("Milestone claimed", v(in, "milestoneLabel", "Structure")),
                new Views.ChipDto("Completion required", "≥ " + num(in, "completionThreshold", "40") + "%"));
    }

    // ---------------------------------------------------------------- instantiation

    /** Build a per-transaction deal, its funding amount and its order-term chips from operator input. */
    public Instance instantiate(String dealType, Map<String, String> inputs) {
        Entry e = entry(dealType == null || !has(dealType) ? MARKETPLACE : dealType);
        Map<String, String> in = inputs == null ? Map.of() : inputs;
        DealDefinition deal = e.dealBuilder().apply(in);
        Money fund = e.fundFullValue() ? deal.contractValue() : trancheValue(deal);
        return new Instance(e.name(), deal, fund, e.orderTermsBuilder().apply(in));
    }

    public Views.OrderFormView orderForm(String dealType) {
        Entry e = entry(dealType == null || !has(dealType) ? MARKETPLACE : dealType);
        String verb = e.fundFullValue() ? "Open a new milestone claim" : "Open a new order";
        return new Views.OrderFormView(e.name(), verb, e.orderForm());
    }

    public record Instance(String dealType, DealDefinition deal, Money fundAmount, List<Views.ChipDto> orderTerms) {}

    // ---------------------------------------------------------------- lookups (unchanged surface)

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

    static Money trancheValue(DealDefinition d) {
        Money v = Money.zero(d.contractValue().currency());
        for (Obligation o : examinedObligations(d)) {
            v = v.plus(o.fullEntitlement(d.contractValue()));
        }
        return v;
    }

    // ---------------------------------------------------------------- input helpers

    private static String v(Map<String, String> in, String key, String def) {
        String s = in.get(key);
        return s == null || s.isBlank() ? def : s.trim();
    }

    /** Read a numeric field, stripping commas/spaces/currency; falls back to the demo default. */
    private static String num(Map<String, String> in, String key, String def) {
        String s = v(in, key, def).replaceAll("[,\\s]", "").replaceAll("[^0-9.]", "");
        if (s.isBlank()) s = def;
        int dot = s.indexOf('.');
        return dot >= 0 ? s.substring(0, dot) : s; // whole units for value/quantity
    }

    private static String fmt(String wholeNumber) {
        try { return String.format("%,d", Long.parseLong(wholeNumber)); }
        catch (NumberFormatException e) { return wholeNumber; }
    }

    public record Entry(String name, DealDefinition deal, boolean fundFullValue,
                        List<Views.PartyView> parties, List<Views.ResponsibilityView> responsibilities,
                        List<Views.ChipDto> chips, List<Views.FieldSpec> orderForm,
                        Function<Map<String, String>, DealDefinition> dealBuilder,
                        Function<Map<String, String>, List<Views.ChipDto>> orderTermsBuilder) {}
}
