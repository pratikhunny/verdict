package com.sc.verdict.examination;

import com.sc.verdict.shared.Ids.ConditionId;
import com.sc.verdict.shared.Ids.FindingId;

import java.util.Objects;

/**
 * A recorded departure of the evidence from a condition.
 *
 * <p>A finding is recorded whether or not it blocks release — a {@link Resolution#CLEARED_BY_TOLERANCE}
 * cosmetic finding still appears on a {@code RELEASE}, because hiding it would defeat the audit
 * trail (scenarios, Pack 2 design note). The {@code resolution} is how the engine, or a party,
 * disposed of it; {@code ruleReference} names the tolerance rule or the clause that applies, so the
 * findings notice can cite it.
 *
 * <p>The id is derived from the condition and grade rather than generated, so that re-examining the
 * same evidence produces the identical finding — a precondition for replay equality.
 */
public record Finding(
        FindingId id,
        ConditionId conditionId,
        FindingGrade grade,
        Resolution resolution,
        String ruleReference,
        double confidence,
        String detail) {

    public Finding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conditionId, "conditionId");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(ruleReference, "ruleReference");
        Objects.requireNonNull(detail, "detail");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0,1]");
        }
    }

    public static Finding of(ConditionId conditionId, FindingGrade grade, Resolution resolution,
                             String ruleReference, double confidence, String detail) {
        return new Finding(new FindingId("F-" + conditionId.value() + "-" + grade.name()),
                conditionId, grade, resolution, ruleReference, confidence, detail);
    }

    /** A finding that still blocks release: not cleared and not approved. */
    public boolean isBlocking() {
        return resolution == Resolution.UNRESOLVED;
    }

    /** Re-issue this finding as approved by an entitled party (the approval-as-evidence loop). */
    public Finding approved(String approvalReference) {
        return new Finding(id, conditionId, grade, Resolution.APPROVED_BY_PARTY,
                approvalReference, confidence, detail);
    }

    /** How a finding was disposed of. */
    public enum Resolution {
        /** Still open — blocks release. */
        UNRESOLVED,
        /** Cleared by a tolerance rule with no party involvement (cosmetic variance). */
        CLEARED_BY_TOLERANCE,
        /** Approved by an entitled party, re-entering the engine as evidence. */
        APPROVED_BY_PARTY
    }
}
