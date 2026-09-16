package com.sc.verdict.app;

import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.contract.Obligation;
import com.sc.verdict.entitlement.EntitlementLedger;
import com.sc.verdict.examination.Determination;
import com.sc.verdict.examination.Finding;
import com.sc.verdict.journal.JournalEntry;
import com.sc.verdict.ledger.FundControlLedger;
import com.sc.verdict.orchestrator.GraphDefinition;
import com.sc.verdict.orchestrator.Node;
import com.sc.verdict.recon.Reconciliation;
import com.sc.verdict.shared.Hashing;
import com.sc.verdict.shared.Money;

import java.util.List;

/** Pure mapping from domain records to {@link Views}. No state, no framework. */
final class Mapper {

    private Mapper() {}

    static Views.MoneyDto money(Money m) {
        return new Views.MoneyDto(m.amount().toPlainString(), m.currency().getCurrencyCode());
    }

    static Views.DealView deal(DealDefinition d) {
        List<Views.ObligationDto> obligations = d.obligations().stream()
                .map(o -> new Views.ObligationDto(
                        o.id().value(), o.payeeId().value(), o.payeeParty().value(),
                        o.entitlementRule().describe(), o.severable(), o.clauseReference(),
                        money(o.fullEntitlement(d.contractValue()))))
                .toList();
        List<Views.ConditionDto> conditions = d.shipmentMilestone().conditions().stream()
                .map(c -> new Views.ConditionDto(c.kind().name(), c.clauseReference()))
                .toList();
        return new Views.DealView(d.dealId().value(), money(d.contractValue()), d.contractedQuantity(),
                d.goodsDescription(), d.latestShipmentDate().toString(), obligations, conditions);
    }

    static Views.DeterminationView determination(Determination det) {
        List<Views.LineDto> disbursements = det.disbursements().stream()
                .map(l -> new Views.LineDto(l.obligation().value(), l.payee().value(),
                        l.payeeParty().value(), money(l.amount())))
                .toList();
        List<Views.ResidualDto> residuals = det.residuals().stream()
                .map(r -> new Views.ResidualDto(r.sourceObligation().value(), r.residualObligation().value(),
                        r.payee().value(), money(r.retained())))
                .toList();
        List<Views.FindingDto> findings = det.findings().stream()
                .map(f -> new Views.FindingDto(f.grade().name(), f.resolution().name(),
                        f.ruleReference(), f.confidence(), f.detail()))
                .toList();
        List<Views.VerdictDto> verdicts = det.verdicts().stream()
                .map(v -> new Views.VerdictDto(v.conditionId().value(), v.status().name(),
                        v.finding().map(Finding::grade).map(Enum::name).orElse(null)))
                .toList();
        return new Views.DeterminationView(
                det.id().value(), det.outcome().name(), money(det.released()), money(det.retained()),
                disbursements, residuals, findings, verdicts, det.renderFindingsNotice(),
                det.ruleSetVersion().value(), det.examinedSubmission().value(),
                Hashing.shortHash(det.evidenceDigest()));
    }

    static Views.LedgerView ledger(FundControlLedger ledger, EntitlementLedger entitlements, java.util.Currency ccy) {
        List<Views.EarmarkDto> earmarks = ledger.activeEarmarks().entrySet().stream()
                .map(e -> new Views.EarmarkDto(e.getKey().value(), money(e.getValue())))
                .toList();
        Reconciliation.Result recon = Reconciliation.check(entitlements, ledger, ccy);
        return new Views.LedgerView(
                money(ledger.heldBalance()), money(ledger.unallocated()),
                money(ledger.reservedTotal()), money(ledger.disbursedTotal()),
                earmarks, new Views.ReconDto(money(recon.entitlementTotal()),
                        money(recon.earmarkTotal()), recon.balanced()));
    }

    static Views.EntryDto entry(JournalEntry e) {
        return new Views.EntryDto(
                e.id().value(), e.recordedAt().toString(), e.determination().id().value(),
                e.determination().outcome().name(), e.ruleSetVersion().value(),
                e.effectiveAt().toString(), Hashing.shortHash(e.determination().evidenceDigest()),
                e.submission().facts().size());
    }

    static Views.GraphView graph(GraphDefinition g) {
        return new Views.GraphView(g.name(), g.sequence().stream().map(Node::name).toList());
    }
}
