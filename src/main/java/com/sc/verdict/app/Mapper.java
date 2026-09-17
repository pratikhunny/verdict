package com.sc.verdict.app;

import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.contract.HeroDealRegistry;
import com.sc.verdict.contract.Obligation;
import com.sc.verdict.entitlement.EntitlementLedger;
import com.sc.verdict.evidence.ExtractedFact;
import com.sc.verdict.evidence.FactKey;
import com.sc.verdict.evidence.Submission;
import com.sc.verdict.examination.Determination;
import com.sc.verdict.examination.Finding;
import com.sc.verdict.examination.ToleranceProfile;
import com.sc.verdict.journal.JournalEntry;
import com.sc.verdict.ledger.FundControlLedger;
import com.sc.verdict.orchestrator.GraphDefinition;
import com.sc.verdict.orchestrator.Node;
import com.sc.verdict.recon.Reconciliation;
import com.sc.verdict.shared.Hashing;
import com.sc.verdict.shared.Money;

import java.util.Currency;
import java.util.List;

/** Pure mapping from domain records to {@link Views}. No state, no framework. */
final class Mapper {

    private Mapper() {}

    static Views.MoneyDto money(Money m) {
        return new Views.MoneyDto(m.amount().toPlainString(), m.currency().getCurrencyCode());
    }

    // ---- transactions ----

    static Views.TransactionView transaction(TransactionService.Txn t, Currency ccy) {
        Money released = t.ledger.disbursedTotal();
        Money retained = t.ledger.reservedTotal();
        String outcome = t.determination != null ? t.determination.outcome().name() : t.status;
        Money milestone = Money.zero(t.deal.contractValue().currency());
        for (Obligation o : t.deal.obligationsFor(t.deal.shipmentMilestone().id())) {
            milestone = milestone.plus(o.fullEntitlement(t.deal.contractValue()));
        }
        return new Views.TransactionView(t.id, t.dealType, t.status, money(t.ledger.heldBalance()),
                money(released), money(retained), outcome, t.uploads.size(),
                t.funded, t.earmarked, t.submission != null, t.determination != null, t.disbursed,
                money(t.fundAmount), money(milestone), t.orderTerms);
    }

    // ---- documents & extraction ----

    static Views.ExtractionResultView extraction(List<UploadedDoc> uploads, Submission submission, String extractor) {
        List<Views.UploadedDocView> docs = uploads.stream()
                .map(d -> new Views.UploadedDocView(d.id(), d.filename(), d.type(), d.sizeBytes(), d.text()))
                .toList();
        List<Views.FactView> facts = submission == null ? List.of() : submission.facts().stream()
                .map(Mapper::fact).toList();
        return new Views.ExtractionResultView(extractor, docs, facts);
    }

    private static Views.FactView fact(ExtractedFact f) {
        return new Views.FactView(fieldLabel(f.key()), f.key().name(), f.value(),
                f.confidence(), f.sourceDocument().value());
    }

    private static String fieldLabel(FactKey key) {
        return switch (key) {
            case EBL_PRESENT -> "Transport document present";
            case EVIDENCED_QUANTITY -> "Quantity";
            case GOODS_DESCRIPTION -> "Goods description";
            case SHIPMENT_DATE -> "Shipment date";
            case APPROVAL_GRANTED -> "Counterparty approval";
            case CERTIFICATE_PRESENT -> "Completion certificate present";
            case COMPLETION_PERCENT -> "Certified completion %";
            case OBJECTION_RAISED -> "Objection / lien raised";
        };
    }

    // ---- determination ----

    static Views.DeterminationView determination(Determination det) {
        List<Views.LineDto> disbursements = det.disbursements().stream()
                .map(l -> new Views.LineDto(l.obligation().value(), l.payee().value(),
                        l.payeeParty().value(), money(l.amount()))).toList();
        List<Views.ResidualDto> residuals = det.residuals().stream()
                .map(r -> new Views.ResidualDto(r.sourceObligation().value(), r.residualObligation().value(),
                        r.payee().value(), money(r.retained()))).toList();
        List<Views.FindingDto> findings = det.findings().stream()
                .map(f -> new Views.FindingDto(f.grade().name(), f.resolution().name(),
                        f.ruleReference(), f.confidence(), f.detail())).toList();
        List<Views.VerdictDto> verdicts = det.verdicts().stream()
                .map(v -> new Views.VerdictDto(v.conditionId().value(), v.status().name(),
                        v.finding().map(Finding::grade).map(Enum::name).orElse(null))).toList();
        return new Views.DeterminationView(det.id().value(), det.outcome().name(),
                money(det.released()), money(det.retained()), disbursements, residuals, findings, verdicts,
                det.renderFindingsNotice(), det.ruleSetVersion().value(), det.examinedSubmission().value(),
                Hashing.shortHash(det.evidenceDigest()));
    }

    // ---- ledger & journal ----

    static Views.LedgerView ledger(FundControlLedger ledger, EntitlementLedger entitlements, Currency ccy,
                                   DealDefinition deal) {
        java.util.Map<String, String> payeeByObligation = new java.util.HashMap<>();
        for (Obligation o : deal.obligations()) {
            payeeByObligation.put(o.id().value(), o.payeeParty().value());
        }
        List<Views.EarmarkDto> earmarks = ledger.activeEarmarks().entrySet().stream()
                .map(e -> {
                    String obl = e.getKey().value();
                    String base = obl.endsWith("-R") ? obl.substring(0, obl.length() - 2) : obl;
                    return new Views.EarmarkDto(obl, payeeByObligation.getOrDefault(base, "—"), money(e.getValue()));
                }).toList();
        Reconciliation.Result recon = Reconciliation.check(entitlements, ledger, ccy);
        return new Views.LedgerView(money(ledger.heldBalance()), money(ledger.unallocated()),
                money(ledger.reservedTotal()), money(ledger.disbursedTotal()), earmarks,
                new Views.ReconDto(money(recon.entitlementTotal()), money(recon.earmarkTotal()), recon.balanced()));
    }

    static Views.EntryDto entry(JournalEntry e) {
        return new Views.EntryDto(e.id().value(), e.recordedAt().toString(), e.determination().id().value(),
                e.determination().outcome().name(), e.ruleSetVersion().value(), e.effectiveAt().toString(),
                Hashing.shortHash(e.determination().evidenceDigest()), e.submission().facts().size());
    }

    static Views.GraphView graph(GraphDefinition g) {
        return new Views.GraphView(g.name(), g.sequence().stream().map(Node::name).toList());
    }
}
