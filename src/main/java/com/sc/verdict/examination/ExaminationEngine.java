package com.sc.verdict.examination;

import com.sc.verdict.contract.Condition;
import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.contract.Milestone;
import com.sc.verdict.contract.Obligation;
import com.sc.verdict.evidence.ExtractedFact;
import com.sc.verdict.evidence.FactKey;
import com.sc.verdict.evidence.Submission;
import com.sc.verdict.shared.Hashing;
import com.sc.verdict.shared.Ids.DeterminationId;
import com.sc.verdict.shared.Money;
import com.sc.verdict.shared.Versions.RuleSetVersion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * L4 — the examination engine. It evaluates a submission's facts against a milestone's conditions
 * and returns a {@link Determination}. It is the core of the product and the core of the demo.
 *
 * <p><strong>It contains no model call and reads no clock.</strong> Every input is supplied — the
 * facts (already asserted by L3), the rule set, the tolerance profile, and the effective instant —
 * and every output is a deterministic function of them (ADR-002, ADR-004; NFR replay determinism).
 * This is what lets a determination replay to an identical result, and it is the only architecture
 * consistent with an agent's limited, non-discretionary duty: the model reads, the engine decides.
 *
 * <p>An architecture test asserts that this package imports no extraction adapter and no money-plane
 * type; if it did, the separation would be a comment rather than a control.
 */
public final class ExaminationEngine {

    /** The pinned rule set of the current build. Callers pass it explicitly so the journal records it. */
    public static final RuleSetVersion RULE_SET = new RuleSetVersion("RS-2026.09-v1");

    /**
     * Examine a submission against the deal's shipment milestone.
     *
     * @return a determination; never null, never throws on a bad-evidence pack — bad evidence is a
     *         {@link Outcome#HOLD}, not an exception
     */
    public Determination examine(DealDefinition deal, Submission submission,
                                 ToleranceProfile tolerance, RuleSetVersion ruleSetVersion, Instant effectiveAt) {
        Objects.requireNonNull(deal, "deal");
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(tolerance, "tolerance");
        Objects.requireNonNull(ruleSetVersion, "ruleSetVersion");
        Objects.requireNonNull(effectiveAt, "effectiveAt");

        Milestone milestone = deal.shipmentMilestone();
        Set<String> approvedGrades = submission.factsFor(FactKey.APPROVAL_GRANTED).stream()
                .map(ExtractedFact::value)
                .collect(Collectors.toSet());

        List<ConditionVerdict> verdicts = new ArrayList<>();
        for (Condition condition : milestone.conditions()) {
            verdicts.add(applyApprovals(evaluate(condition, deal, submission, tolerance), approvedGrades));
        }

        List<Finding> findings = verdicts.stream()
                .map(ConditionVerdict::finding)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        Outcome outcome = decideOutcome(verdicts, findings, deal, milestone);

        // Money lines depend only on the outcome and the evidenced quantity.
        List<Obligation> obligations = deal.obligationsFor(milestone.id());
        var disbursements = new ArrayList<DisbursementLine>();
        var residuals = new ArrayList<ResidualLine>();
        Money released = Money.zero(deal.contractValue().currency());
        Money retained = Money.zero(deal.contractValue().currency());

        if (outcome == Outcome.RELEASE) {
            for (Obligation o : obligations) {
                Money full = o.fullEntitlement(deal.contractValue());
                disbursements.add(new DisbursementLine(o.id(), o.payeeId(), o.payeeParty(), full));
                released = released.plus(full);
            }
        } else if (outcome == Outcome.PARTIAL_RELEASE) {
            BigDecimal ratio = evidencedRatio(submission, deal);
            for (Obligation o : obligations) {
                Money full = o.fullEntitlement(deal.contractValue());
                Money pay = full.multiply(ratio);
                Money keep = full.minus(pay); // by subtraction, so no rounding leak
                disbursements.add(new DisbursementLine(o.id(), o.payeeId(), o.payeeParty(), pay));
                if (!keep.isZero()) {
                    residuals.add(new ResidualLine(o.id(), o.asResidual(o.entitlementRule()).id(), o.payeeId(), keep));
                }
                released = released.plus(pay);
                retained = retained.plus(keep);
            }
        }
        // HOLD and HOLD_PENDING_APPROVAL move nothing; the existing earmark is untouched.

        String digest = evidenceDigest(submission);
        return new Determination(
                new DeterminationId("DET-" + submission.id().value()),
                deal.dealId(), milestone.id(), outcome, ruleSetVersion, effectiveAt,
                submission.id(), digest, verdicts, findings, disbursements, residuals, released, retained);
    }

    // ---------------------------------------------------------------- condition evaluation

    private ConditionVerdict evaluate(Condition condition, DealDefinition deal,
                                      Submission submission, ToleranceProfile tolerance) {
        FactKey primary = primaryKey(condition.kind());
        Optional<ExtractedFact> pf = submission.fact(primary);

        // Fail closed on missing or low-confidence evidence, except that an absent eBL is a
        // MISSING_DOCUMENT finding rather than an indeterminate one — the document simply is not there.
        if (pf.isEmpty()) {
            if (condition.kind() == Condition.Kind.DOCUMENT_PRESENT) {
                return ConditionVerdict.notMet(condition.id(), Finding.of(condition.id(),
                        FindingGrade.MISSING_DOCUMENT, Finding.Resolution.UNRESOLVED,
                        condition.clauseReference(), 1.0, "required transport document not present"));
            }
            return ConditionVerdict.indeterminate(condition.id(), Finding.of(condition.id(),
                    FindingGrade.SUBSTANTIVE, Finding.Resolution.UNRESOLVED,
                    condition.clauseReference(), 0.0, "no evidence asserted for " + primary));
        }
        ExtractedFact fact = pf.get();
        if (fact.confidence() < tolerance.minConfidence()) {
            return ConditionVerdict.indeterminate(condition.id(), Finding.of(condition.id(),
                    FindingGrade.SUBSTANTIVE, Finding.Resolution.UNRESOLVED,
                    "confidence floor " + tolerance.minConfidence(), fact.confidence(),
                    "extraction confidence %.2f below floor — fail closed".formatted(fact.confidence())));
        }

        return switch (condition.kind()) {
            case DOCUMENT_PRESENT -> fact.asBoolean()
                    ? ConditionVerdict.met(condition.id())
                    : ConditionVerdict.notMet(condition.id(), Finding.of(condition.id(),
                        FindingGrade.MISSING_DOCUMENT, Finding.Resolution.UNRESOLVED,
                        condition.clauseReference(), fact.confidence(), "transport document reported absent"));
            case GOODS_DESCRIPTION_MATCHES -> evaluateGoods(condition, deal, fact, tolerance);
            case QUANTITY_MATCHES -> evaluateQuantity(condition, deal, fact);
            case SHIPPED_WITHIN_LATEST_DATE -> evaluateShipDate(condition, deal, fact);
        };
    }

    private ConditionVerdict evaluateGoods(Condition condition, DealDefinition deal,
                                           ExtractedFact fact, ToleranceProfile tolerance) {
        String contract = deal.goodsDescription();
        String evidence = fact.value();
        if (tolerance.goodsExact(contract, evidence)) {
            return ConditionVerdict.met(condition.id());
        }
        if (tolerance.goodsEquivalent(contract, evidence)) {
            return ConditionVerdict.metWith(condition.id(), Finding.of(condition.id(),
                    FindingGrade.COSMETIC, Finding.Resolution.CLEARED_BY_TOLERANCE,
                    tolerance.ruleReference(), fact.confidence(),
                    "goods \"%s\" vs contract \"%s\" — cleared within tolerance".formatted(evidence, contract)));
        }
        return ConditionVerdict.notMet(condition.id(), Finding.of(condition.id(),
                FindingGrade.SUBSTANTIVE, Finding.Resolution.UNRESOLVED,
                condition.clauseReference(), fact.confidence(),
                "goods \"%s\" not equivalent to contract \"%s\"".formatted(evidence, contract)));
    }

    private ConditionVerdict evaluateQuantity(Condition condition, DealDefinition deal, ExtractedFact fact) {
        long evidenced = fact.asLong();
        long contracted = deal.contractedQuantity();
        if (evidenced == contracted) {
            return ConditionVerdict.met(condition.id());
        }
        if (evidenced < contracted) {
            return ConditionVerdict.notMet(condition.id(), Finding.of(condition.id(),
                    FindingGrade.QUANTITY, Finding.Resolution.UNRESOLVED,
                    condition.clauseReference(), fact.confidence(),
                    "evidenced %d, contracted %d — short by %d".formatted(evidenced, contracted, contracted - evidenced)));
        }
        return ConditionVerdict.notMet(condition.id(), Finding.of(condition.id(),
                FindingGrade.SUBSTANTIVE, Finding.Resolution.UNRESOLVED,
                condition.clauseReference(), fact.confidence(),
                "evidenced %d exceeds contracted %d — over-shipment".formatted(evidenced, contracted)));
    }

    private ConditionVerdict evaluateShipDate(Condition condition, DealDefinition deal, ExtractedFact fact) {
        LocalDate shipped = LocalDate.parse(fact.value());
        LocalDate latest = deal.latestShipmentDate();
        if (!shipped.isAfter(latest)) {
            return ConditionVerdict.met(condition.id());
        }
        long daysLate = ChronoUnit.DAYS.between(latest, shipped);
        return ConditionVerdict.notMet(condition.id(), Finding.of(condition.id(),
                FindingGrade.TIMING, Finding.Resolution.UNRESOLVED,
                condition.clauseReference(), fact.confidence(),
                "shipped %s, latest %s — %d day(s) late".formatted(shipped, latest, daysLate)));
    }

    /** If a blocking finding's grade has been approved by an entitled party, treat the condition as met. */
    private ConditionVerdict applyApprovals(ConditionVerdict verdict, Set<String> approvedGrades) {
        if (verdict.finding().isEmpty()) {
            return verdict;
        }
        Finding f = verdict.finding().get();
        if (f.isBlocking() && f.grade().isPartyApprovable() && approvedGrades.contains(f.grade().name())) {
            return ConditionVerdict.metWith(verdict.conditionId(),
                    f.approved("approval admitted by authority gate"));
        }
        return verdict;
    }

    // ---------------------------------------------------------------- outcome

    private Outcome decideOutcome(List<ConditionVerdict> verdicts, List<Finding> findings,
                                  DealDefinition deal, Milestone milestone) {
        boolean anyIndeterminate = verdicts.stream()
                .anyMatch(v -> v.status() == ConditionVerdict.Status.INDETERMINATE);
        if (anyIndeterminate) {
            return Outcome.HOLD; // fail closed
        }

        List<Finding> blocking = findings.stream().filter(Finding::isBlocking).toList();
        if (blocking.isEmpty()) {
            return Outcome.RELEASE;
        }

        Set<FindingGrade> blockingGrades = blocking.stream()
                .map(Finding::grade)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(FindingGrade.class)));

        // A grade that is neither severable nor party-approvable can only be held.
        boolean hasHardBlock = blockingGrades.stream()
                .anyMatch(g -> !g.isSeverable() && !g.isPartyApprovable());
        if (hasHardBlock) {
            return Outcome.HOLD;
        }
        // A party-approvable finding (timing) drafts an approval and waits — nothing moves.
        if (blockingGrades.stream().anyMatch(FindingGrade::isPartyApprovable)) {
            return Outcome.HOLD_PENDING_APPROVAL;
        }
        // What remains is a severable shortfall (quantity); partial release requires severable obligations.
        boolean obligationsSeverable = deal.obligationsFor(milestone.id()).stream().allMatch(Obligation::severable);
        return obligationsSeverable ? Outcome.PARTIAL_RELEASE : Outcome.HOLD;
    }

    // ---------------------------------------------------------------- helpers

    private BigDecimal evidencedRatio(Submission submission, DealDefinition deal) {
        long evidenced = submission.fact(FactKey.EVIDENCED_QUANTITY).map(ExtractedFact::asLong).orElse(0L);
        return new BigDecimal(evidenced).divide(new BigDecimal(deal.contractedQuantity()), 10, RoundingMode.HALF_UP);
    }

    private static FactKey primaryKey(Condition.Kind kind) {
        return switch (kind) {
            case DOCUMENT_PRESENT -> FactKey.EBL_PRESENT;
            case GOODS_DESCRIPTION_MATCHES -> FactKey.GOODS_DESCRIPTION;
            case QUANTITY_MATCHES -> FactKey.EVIDENCED_QUANTITY;
            case SHIPPED_WITHIN_LATEST_DATE -> FactKey.SHIPMENT_DATE;
        };
    }

    /** A stable hash over the examined facts, for display and for the journal's evidence pin. */
    private static String evidenceDigest(Submission submission) {
        String canonical = submission.facts().stream()
                .map(f -> f.key() + "=" + f.value() + "@" + f.confidence() + "#" + f.sourceHash())
                .sorted()
                .collect(Collectors.joining("|"));
        return Hashing.sha256Hex(canonical);
    }
}
