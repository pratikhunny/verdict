package com.sc.verdict.demo;

import com.sc.verdict.contract.Condition;
import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.contract.EntitlementRule;
import com.sc.verdict.contract.Milestone;
import com.sc.verdict.contract.Obligation;
import com.sc.verdict.evidence.Document;
import com.sc.verdict.evidence.ExtractedFact;
import com.sc.verdict.evidence.FactKey;
import com.sc.verdict.evidence.Submission;
import com.sc.verdict.examination.Determination;
import com.sc.verdict.examination.ExaminationEngine;
import com.sc.verdict.examination.Outcome;
import com.sc.verdict.examination.ToleranceProfile;
import com.sc.verdict.shared.Hashing;
import com.sc.verdict.shared.Ids.ConditionId;
import com.sc.verdict.shared.Ids.ContractVersionId;
import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.DocumentId;
import com.sc.verdict.shared.Ids.MilestoneId;
import com.sc.verdict.shared.Ids.ObligationId;
import com.sc.verdict.shared.Ids.PartyId;
import com.sc.verdict.shared.Ids.PayeeId;
import com.sc.verdict.shared.Ids.SubmissionId;
import com.sc.verdict.shared.Money;
import com.sc.verdict.shared.Versions.AdapterVersion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptability proof: a <em>second</em>, structurally different deal type modelled entirely as data
 * and run through the <strong>same</strong> examination engine — no new nodes, no engine change
 * (ADR-004). It exists to answer "can the platform do other use cases?" in Q&A; the marketplace deal
 * remains the demo.
 *
 * <p>The deal is a single-payee equipment import whose obligation is <strong>non-severable</strong>.
 * That one flag makes the same engine behave differently: a short delivery becomes a {@code HOLD}
 * (you cannot part-pay a whole machine) where the marketplace deal — severable — became a
 * {@code PARTIAL_RELEASE}. Same nodes, different configuration, different outcome. What was written
 * to add this: a {@link DealDefinition} and its evidence. Zero production code.
 */
public final class AdaptabilityScenario {

    private static final Instant AT = Instant.parse("2026-10-01T09:00:00Z");
    private static final ToleranceProfile TOLERANCE = ToleranceProfile.demoDefault();
    private static final AdapterVersion EXT = new AdapterVersion("EXT-EQUIP-fixture-v1");

    private static final DealId DEAL = new DealId("DEAL-EQUIP-2026-0002");
    private static final ContractVersionId CV = new ContractVersionId("CTR-EQUIP-v1");
    private static final MilestoneId M_DELIVERY = new MilestoneId("MS-DELIVERY");
    private static final PartyId BUYER = new PartyId("PTY-PLANT-OPERATOR");
    private static final PartyId SUPPLIER = new PartyId("PTY-EQUIP-MAKER");
    private static final PayeeId PAYEE = new PayeeId("PAYEE-EQUIP-SUPPLIER");
    private static final LocalDate LATEST_DELIVERY = LocalDate.parse("2026-10-15");

    private final ExaminationEngine engine = new ExaminationEngine();
    private final DealDefinition deal = buildDeal();
    private final List<Row> rows = new ArrayList<>();

    public static void main(String[] args) {
        new AdaptabilityScenario().run();
    }

    private void run() {
        // Clean delivery — all met → RELEASE in full to the single payee.
        Determination clean = examine("clean", 200, "industrial sensors", "2026-10-10");
        check("Equipment · clean → RELEASE", Outcome.RELEASE, clean.outcome());
        check("Equipment · released in full", Money.of("500000.00", "USD"), clean.released());
        check("Equipment · single payee", 1, clean.disbursements().size());

        // Short delivery — quantity short, but obligation NON-severable → HOLD (not partial).
        Determination shortD = examine("short", 150, "industrial sensors", "2026-10-10");
        check("Equipment · short (non-severable) → HOLD", Outcome.HOLD, shortD.outcome());
        check("Equipment · nothing released on hold", Money.of("0.00", "USD"), shortD.released());
        check("Equipment · QUANTITY finding recorded", "QUANTITY", shortD.findings().get(0).grade().name());

        // Late delivery — timing is approvable → HOLD_PENDING_APPROVAL, same as the marketplace deal.
        Determination late = examine("late", 200, "industrial sensors", "2026-10-20");
        check("Equipment · late → HOLD_PENDING_APPROVAL", Outcome.HOLD_PENDING_APPROVAL, late.outcome());

        print();
        long failures = rows.stream().filter(r -> !r.passed).count();
        if (failures > 0) {
            System.out.printf("%n%d expectation(s) failed.%n", failures);
            System.exit(1);
        }
        System.out.printf("%nAll %d held. A new deal type is data: one DealDefinition, zero new nodes.%n", rows.size());
    }

    // ---------------------------------------------------------------- the deal, as data

    private static DealDefinition buildDeal() {
        Milestone delivery = new Milestone(M_DELIVERY, "Delivery evidenced", List.of(
                new Condition(new ConditionId("C-DO-PRESENT"), Condition.Kind.DOCUMENT_PRESENT, "cl. 2 — delivery order"),
                new Condition(new ConditionId("C-EQ-GOODS"), Condition.Kind.GOODS_DESCRIPTION_MATCHES, "cl. 3 — description"),
                new Condition(new ConditionId("C-EQ-QTY"), Condition.Kind.QUANTITY_MATCHES, "cl. 4 — quantity"),
                new Condition(new ConditionId("C-EQ-DATE"), Condition.Kind.SHIPPED_WITHIN_LATEST_DATE, "cl. 5 — latest delivery")));

        // One obligation, 100% to the supplier, NON-severable — the flag that changes the behaviour.
        Obligation payment = new Obligation(new ObligationId("EQ1"), CV, M_DELIVERY, PAYEE,
                BUYER, SUPPLIER, EntitlementRule.fixedPercentage("1.00"),
                false, "cl. 6 — payment on delivery", 1, null);

        return new DealDefinition(DEAL, CV, Money.of("500000.00", "USD"), 200,
                "industrial sensors", LATEST_DELIVERY, delivery, List.of(payment));
    }

    private Determination examine(String tag, long quantity, String goods, String deliveredDate) {
        String content = "delivery-order|units=%d|goods=%s|delivered=%s".formatted(quantity, goods, deliveredDate);
        DocumentId docId = new DocumentId("DOC-DO-" + tag.toUpperCase());
        String hash = Hashing.sha256Hex(content);
        List<ExtractedFact> facts = List.of(
                new ExtractedFact(FactKey.EBL_PRESENT, "true", 0.99, docId, hash, EXT),
                new ExtractedFact(FactKey.EVIDENCED_QUANTITY, Long.toString(quantity), 0.99, docId, hash, EXT),
                new ExtractedFact(FactKey.GOODS_DESCRIPTION, goods, 0.99, docId, hash, EXT),
                new ExtractedFact(FactKey.SHIPMENT_DATE, deliveredDate, 0.99, docId, hash, EXT));
        Submission submission = new Submission(new SubmissionId("SUB-EQUIP-" + tag.toUpperCase()),
                DEAL, List.of(new Document(docId, "delivery-order", hash)), facts, AT);
        return engine.examine(deal, submission, TOLERANCE, ExaminationEngine.RULE_SET, AT);
    }

    // ---------------------------------------------------------------- harness

    private void check(String label, Object expected, Object actual) {
        rows.add(new Row(label, String.valueOf(expected), String.valueOf(actual),
                java.util.Objects.equals(expected, actual)));
    }

    private void print() {
        System.out.printf("%n%-46s %-26s %-26s %s%n", "EXPECTATION", "EXPECTED", "ACTUAL", "");
        System.out.println("-".repeat(104));
        for (Row r : rows) {
            System.out.printf("%-46s %-26s %-26s %s%n", r.label, r.expected, r.actual, r.passed ? "OK" : "FAIL");
        }
    }

    private record Row(String label, String expected, String actual, boolean passed) {}
}
