package com.sc.verdict.demo;

import com.sc.verdict.casework.ApprovalRequest;
import com.sc.verdict.casework.ApprovalWorkflow;
import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.contract.HeroDealRegistry;
import com.sc.verdict.contract.Obligation;
import com.sc.verdict.entitlement.EntitlementLedger;
import com.sc.verdict.evidence.EvidencePack;
import com.sc.verdict.evidence.ExtractedFact;
import com.sc.verdict.evidence.FixtureExtraction;
import com.sc.verdict.evidence.Submission;
import com.sc.verdict.examination.Determination;
import com.sc.verdict.examination.DisbursementLine;
import com.sc.verdict.examination.ExaminationEngine;
import com.sc.verdict.examination.Outcome;
import com.sc.verdict.examination.ResidualLine;
import com.sc.verdict.examination.ToleranceProfile;
import com.sc.verdict.journal.DecisionJournal;
import com.sc.verdict.journal.JournalEntry;
import com.sc.verdict.journal.ReplayEngine;
import com.sc.verdict.ledger.FundControlLedger;
import com.sc.verdict.party.AdmissionDecision;
import com.sc.verdict.party.AuthorityPolicy;
import com.sc.verdict.party.HeroPartyFixtures;
import com.sc.verdict.party.Instruction;
import com.sc.verdict.party.Mandate;
import com.sc.verdict.recon.Reconciliation;
import com.sc.verdict.shared.Ids.InstructionId;
import com.sc.verdict.shared.Ids.PayeeId;
import com.sc.verdict.shared.Ids.SubmissionId;
import com.sc.verdict.shared.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * The four evidence packs as an executable specification (scenarios §The four evidence packs).
 * Same contract, same engine, same rule-set version — four determinations. Each pack asserts the
 * exact outcome, released amount, multi-payee split and reconciliation stated in the catalogue; the
 * harness exits non-zero if any expectation fails.
 *
 * <p>It also proves the two claims the demo turns on: the short-shipment determination replays to an
 * identical result from the journal, and the late-shipment approval passes the real L0 authority
 * gate (Treasury alone is short of the ceiling; the CFO countersigns) before re-entering the engine
 * as evidence.
 *
 * <pre>{@code javac -d out $(find src -name '*.java') && java -cp out com.sc.verdict.demo.EvidencePackScenarios}</pre>
 */
public final class EvidencePackScenarios {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant EXAM_AT = Instant.parse("2026-09-15T09:00:00Z");
    private static final ToleranceProfile TOLERANCE = ToleranceProfile.demoDefault();

    private final HeroDealRegistry registry = new HeroDealRegistry();
    private final DealDefinition deal = registry.heroDeal();
    private final FixtureExtraction extraction = new FixtureExtraction();
    private final ExaminationEngine engine = new ExaminationEngine();
    private final HeroPartyFixtures parties = new HeroPartyFixtures();
    private final AuthorityPolicy authority = new AuthorityPolicy(parties, parties, parties);

    private final List<Row> rows = new ArrayList<>();

    public static void main(String[] args) {
        new EvidencePackScenarios().run();
    }

    private void run() {
        pack1Clean();
        pack2Cosmetic();
        DecisionJournal shortShipmentJournal = pack3ShortShipment();
        pack3Replay(shortShipmentJournal);
        pack4LateShipmentApprovalLoop();

        print();
        long failures = rows.stream().filter(r -> !r.passed).count();
        if (failures > 0) {
            System.out.printf("%n%d expectation(s) failed.%n", failures);
            System.exit(1);
        }
        System.out.printf("%nAll %d expectations held. Same contract, same engine, four determinations.%n", rows.size());
    }

    // ---------------------------------------------------------------- Pack 1

    private void pack1Clean() {
        var ledger = fundedLedger();
        var entitlements = seededEntitlements();

        Determination det = examine(EvidencePack.CLEAN);
        ledger.apply(det);
        entitlements.apply(det);

        check("Pack 1 · outcome", Outcome.RELEASE, det.outcome());
        check("Pack 1 · released", Money.of("600000.00", "USD"), det.released());
        check("Pack 1 · retained", Money.of("0.00", "USD"), det.retained());
        check("Pack 1 · supplier split", Money.of("576000.00", "USD"), disbursed(det, HeroDealRegistry.PAYEE_SUPPLIER));
        check("Pack 1 · marketplace split", Money.of("19800.00", "USD"), disbursed(det, HeroDealRegistry.PAYEE_MARKETPLACE));
        check("Pack 1 · bank fee split", Money.of("4200.00", "USD"), disbursed(det, HeroDealRegistry.PAYEE_BANK));
        check("Pack 1 · no findings", 0, det.findings().size());
        check("Pack 1 · reconciliation balanced", true, reconcile(entitlements, ledger));
    }

    // ---------------------------------------------------------------- Pack 2

    private void pack2Cosmetic() {
        var ledger = fundedLedger();
        var entitlements = seededEntitlements();

        Determination det = examine(EvidencePack.COSMETIC_VARIANCE);
        ledger.apply(det);
        entitlements.apply(det);

        check("Pack 2 · outcome", Outcome.RELEASE, det.outcome());
        check("Pack 2 · released", Money.of("600000.00", "USD"), det.released());
        check("Pack 2 · one finding recorded", 1, det.findings().size());
        var finding = det.findings().get(0);
        check("Pack 2 · finding grade", "COSMETIC", finding.grade().name());
        check("Pack 2 · finding cleared by tolerance", "CLEARED_BY_TOLERANCE", finding.resolution().name());
        check("Pack 2 · finding not blocking", false, finding.isBlocking());
        check("Pack 2 · tolerance rule cited", true, finding.ruleReference().startsWith("TOL-2026.09"));
        check("Pack 2 · extraction confidence ~0.94", "0.94", "%.2f".formatted(finding.confidence()));
        check("Pack 2 · reconciliation balanced", true, reconcile(entitlements, ledger));
    }

    // ---------------------------------------------------------------- Pack 3

    private DecisionJournal pack3ShortShipment() {
        var ledger = fundedLedger();
        var entitlements = seededEntitlements();
        var journal = new DecisionJournal();

        Determination det = examine(EvidencePack.SHORT_SHIPMENT);
        journal.append(deal, extraction.extract(EvidencePack.SHORT_SHIPMENT, HeroDealRegistry.DEAL, EXAM_AT),
                TOLERANCE, ExaminationEngine.RULE_SET, EXAM_AT, det, EXAM_AT);
        ledger.apply(det);
        entitlements.apply(det);

        check("Pack 3 · outcome", Outcome.PARTIAL_RELEASE, det.outcome());
        check("Pack 3 · released (80%)", Money.of("480000.00", "USD"), det.released());
        check("Pack 3 · retained (20%)", Money.of("120000.00", "USD"), det.retained());
        check("Pack 3 · supplier split", Money.of("460800.00", "USD"), disbursed(det, HeroDealRegistry.PAYEE_SUPPLIER));
        check("Pack 3 · marketplace split", Money.of("15840.00", "USD"), disbursed(det, HeroDealRegistry.PAYEE_MARKETPLACE));
        check("Pack 3 · bank fee split", Money.of("3360.00", "USD"), disbursed(det, HeroDealRegistry.PAYEE_BANK));
        check("Pack 3 · supplier residual re-earmarked", Money.of("115200.00", "USD"), residual(det, HeroDealRegistry.PAYEE_SUPPLIER));
        check("Pack 3 · one QUANTITY finding", "QUANTITY", det.findings().get(0).grade().name());
        check("Pack 3 · finding blocks", true, det.findings().get(0).isBlocking());
        check("Pack 3 · ledger held balance", Money.of("520000.00", "USD"), ledger.heldBalance());
        check("Pack 3 · ledger disbursed", Money.of("480000.00", "USD"), ledger.disbursedTotal());
        check("Pack 3 · reconciliation balanced", true, reconcile(entitlements, ledger));
        return journal;
    }

    private void pack3Replay(DecisionJournal journal) {
        var replayEngine = new ReplayEngine(engine);
        JournalEntry entry = journal.entries().get(0);
        var result = replayEngine.replay(entry);
        check("Pack 3 · replay identical", true, result.identical());
        check("Pack 3 · replay same outcome", entry.determination().outcome(), result.replayed().outcome());
        check("Pack 3 · replay same released", entry.determination().released(), result.replayed().released());
    }

    // ---------------------------------------------------------------- Pack 4

    private void pack4LateShipmentApprovalLoop() {
        var ledger = fundedLedger();
        var entitlements = seededEntitlements();

        // Stage 1 — examine late shipment → HOLD_PENDING_APPROVAL, nothing moves.
        Submission late = extraction.extract(EvidencePack.LATE_SHIPMENT, HeroDealRegistry.DEAL, EXAM_AT);
        Determination hpa = engine.examine(deal, late, TOLERANCE, ExaminationEngine.RULE_SET, EXAM_AT);
        ledger.apply(hpa);
        entitlements.apply(hpa);

        check("Pack 4.1 · outcome", Outcome.HOLD_PENDING_APPROVAL, hpa.outcome());
        check("Pack 4.1 · nothing released", Money.of("0.00", "USD"), hpa.released());
        check("Pack 4.1 · TIMING finding blocks", "TIMING", hpa.blockingFindings().get(0).grade().name());
        check("Pack 4.1 · funds still fully earmarked", Money.of("1000000.00", "USD"), ledger.reservedTotal());
        check("Pack 4.1 · reconciliation balanced", true, reconcile(entitlements, ledger));

        // Stage 2 — buyer approves. Presented as an instruction; must pass the admission gate.
        var workflow = new ApprovalWorkflow();
        ApprovalRequest request = workflow.draft(hpa, HeroPartyFixtures.BUYER,
                Money.of("600000", "USD"), LocalDate.parse("2026-09-15"));

        Instruction treasuryAlone = new Instruction(new InstructionId("INS-APPROVAL-1"),
                HeroPartyFixtures.DEAL, HeroPartyFixtures.BUYER, HeroPartyFixtures.BUYER_TREASURY,
                Mandate.InstructionType.WAIVE_DISCREPANCY, Money.of("600000", "USD"), List.of(), EXAM_AT);
        AdmissionDecision alone = authority.admit(treasuryAlone);
        check("Pack 4.2 · Treasury alone short of ceiling",
                "RequiresCountersignature", alone.getClass().getSimpleName());

        Instruction dualSigned = new Instruction(new InstructionId("INS-APPROVAL-2"),
                HeroPartyFixtures.DEAL, HeroPartyFixtures.BUYER, HeroPartyFixtures.BUYER_TREASURY,
                Mandate.InstructionType.WAIVE_DISCREPANCY, Money.of("600000", "USD"),
                List.of(HeroPartyFixtures.BUYER_CFO), EXAM_AT);
        AdmissionDecision dual = authority.admit(dualSigned);
        check("Pack 4.2 · CFO countersigns → admitted", true, dual.isAdmitted());

        // Stage 3 — the approval enters as evidence; the engine re-examines.
        ExtractedFact approvalFact = workflow.asEvidence(request,
                ((AdmissionDecision.Admitted) dual).rationale(), EXAM_AT);
        Submission reexamined = late.withAdditionalFacts(
                new SubmissionId("SUB-LATE_SHIPMENT-APPROVED"), EXAM_AT, List.of(approvalFact));
        Determination released = engine.examine(deal, reexamined, TOLERANCE, ExaminationEngine.RULE_SET, EXAM_AT);
        ledger.apply(released);
        entitlements.apply(released);

        check("Pack 4.3 · re-examination releases", Outcome.RELEASE, released.outcome());
        check("Pack 4.3 · released in full", Money.of("600000.00", "USD"), released.released());
        check("Pack 4.3 · TIMING finding now approved", "APPROVED_BY_PARTY",
                findingByGrade(released, "TIMING").resolution().name());
        check("Pack 4.3 · reconciliation balanced", true, reconcile(entitlements, ledger));
    }

    // ---------------------------------------------------------------- helpers

    private Determination examine(EvidencePack pack) {
        Submission submission = extraction.extract(pack, HeroDealRegistry.DEAL, EXAM_AT);
        return engine.examine(deal, submission, TOLERANCE, ExaminationEngine.RULE_SET, EXAM_AT);
    }

    /** A freshly funded ledger with every obligation earmarked at full entitlement. */
    private FundControlLedger fundedLedger() {
        var ledger = new FundControlLedger(USD);
        ledger.fund(deal.contractValue(), "SETUP", EXAM_AT);
        for (Obligation o : deal.obligations()) {
            ledger.earmark(o.id(), o.fullEntitlement(deal.contractValue()), "SETUP", EXAM_AT);
        }
        return ledger;
    }

    private EntitlementLedger seededEntitlements() {
        var e = new EntitlementLedger();
        e.seedFromDeal(deal);
        return e;
    }

    private boolean reconcile(EntitlementLedger entitlements, FundControlLedger ledger) {
        return Reconciliation.check(entitlements, ledger, USD).balanced();
    }

    private static Money disbursed(Determination det, PayeeId payee) {
        return det.disbursements().stream()
                .filter(l -> l.payee().equals(payee))
                .map(DisbursementLine::amount)
                .findFirst().orElse(Money.zero(USD));
    }

    private static Money residual(Determination det, PayeeId payee) {
        return det.residuals().stream()
                .filter(l -> l.payee().equals(payee))
                .map(ResidualLine::retained)
                .findFirst().orElse(Money.zero(USD));
    }

    private static com.sc.verdict.examination.Finding findingByGrade(Determination det, String grade) {
        return det.findings().stream()
                .filter(f -> f.grade().name().equals(grade))
                .findFirst().orElseThrow();
    }

    private void check(String label, Object expected, Object actual) {
        rows.add(new Row(label, String.valueOf(expected), String.valueOf(actual),
                java.util.Objects.equals(expected, actual)));
    }

    private void print() {
        System.out.printf("%n%-42s %-26s %-26s %s%n", "EXPECTATION", "EXPECTED", "ACTUAL", "");
        System.out.println("-".repeat(104));
        for (Row r : rows) {
            System.out.printf("%-42s %-26s %-26s %s%n",
                    r.label, truncate(r.expected, 25), truncate(r.actual, 25), r.passed ? "OK" : "FAIL");
        }
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n - 1) + "…");
    }

    private record Row(String label, String expected, String actual, boolean passed) {}
}
