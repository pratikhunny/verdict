package com.sc.verdict.examination;

import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.DeterminationId;
import com.sc.verdict.shared.Ids.MilestoneId;
import com.sc.verdict.shared.Ids.SubmissionId;
import com.sc.verdict.shared.Money;
import com.sc.verdict.shared.Versions.RuleSetVersion;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The determination — the single contract from the decision plane to the money plane (ADR-001).
 *
 * <p>It says what should happen ({@link Outcome}), how much moves and to whom ({@code disbursements}),
 * what is retained ({@code residuals}), and — for the record, not for the money plane — why
 * ({@code verdicts}, {@code findings}). The money plane reads only the outcome and the money lines;
 * it never reads a finding, and it never writes back. Nothing crosses the boundary in reverse.
 *
 * <p>Every field is derived deterministically from the examined inputs and the pinned
 * {@code ruleSetVersion}, so two examinations of the same evidence produce an equal determination —
 * this is what the replay in L8 asserts. There are no generated ids or clock reads inside it.
 */
public record Determination(
        DeterminationId id,
        DealId dealId,
        MilestoneId milestoneId,
        Outcome outcome,
        RuleSetVersion ruleSetVersion,
        Instant effectiveAt,
        SubmissionId examinedSubmission,
        String evidenceDigest,
        List<ConditionVerdict> verdicts,
        List<Finding> findings,
        List<DisbursementLine> disbursements,
        List<ResidualLine> residuals,
        Money released,
        Money retained) {

    public Determination {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dealId, "dealId");
        Objects.requireNonNull(milestoneId, "milestoneId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(ruleSetVersion, "ruleSetVersion");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(examinedSubmission, "examinedSubmission");
        Objects.requireNonNull(evidenceDigest, "evidenceDigest");
        Objects.requireNonNull(released, "released");
        Objects.requireNonNull(retained, "retained");
        verdicts = List.copyOf(verdicts);
        findings = List.copyOf(findings);
        disbursements = List.copyOf(disbursements);
        residuals = List.copyOf(residuals);
    }

    /** The blocking findings — those that still stand in the way of release. */
    public List<Finding> blockingFindings() {
        return findings.stream().filter(Finding::isBlocking).toList();
    }

    /**
     * A single, itemised findings notice citing each finding's clause — the artefact issued to the
     * counterparty on a hold or partial release (control matrix: one notice, each finding, clause
     * cited). Cleared and approved findings are listed too, so the notice is a complete record.
     */
    public String renderFindingsNotice() {
        var sb = new StringBuilder();
        sb.append("FINDINGS NOTICE — ").append(dealId).append(" · ").append(milestoneId).append('\n');
        sb.append("Determination: ").append(outcome).append(" (rule set ").append(ruleSetVersion).append(")\n");
        if (findings.isEmpty()) {
            sb.append("  No findings.\n");
            return sb.toString();
        }
        int n = 1;
        for (Finding f : findings) {
            sb.append("  ").append(n++).append(". ")
                    .append(f.grade())
                    .append(" [").append(f.resolution()).append("] — ")
                    .append(f.detail())
                    .append("  (").append(f.ruleReference()).append(')')
                    .append(f.confidence() < 1.0 ? "  conf %.2f".formatted(f.confidence()) : "")
                    .append('\n');
        }
        return sb.toString();
    }
}
