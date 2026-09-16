package com.sc.verdict.examination;

import com.sc.verdict.shared.Ids.ConditionId;

import java.util.Objects;
import java.util.Optional;

/**
 * The engine's verdict on a single condition, with the finding it produced if it did not pass.
 *
 * <p>{@link Status#INDETERMINATE} is the fail-closed state: the evidence could not be evaluated with
 * enough confidence, so the condition neither passes nor cleanly fails. An indeterminate condition
 * never releases (NFR: fail-closed).
 */
public record ConditionVerdict(ConditionId conditionId, Status status, Optional<Finding> finding) {

    public ConditionVerdict {
        Objects.requireNonNull(conditionId, "conditionId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(finding, "finding");
    }

    public static ConditionVerdict met(ConditionId id) {
        return new ConditionVerdict(id, Status.MET, Optional.empty());
    }

    public static ConditionVerdict metWith(ConditionId id, Finding finding) {
        return new ConditionVerdict(id, Status.MET, Optional.of(finding));
    }

    public static ConditionVerdict notMet(ConditionId id, Finding finding) {
        return new ConditionVerdict(id, Status.NOT_MET, Optional.of(finding));
    }

    public static ConditionVerdict indeterminate(ConditionId id, Finding finding) {
        return new ConditionVerdict(id, Status.INDETERMINATE, Optional.of(finding));
    }

    public enum Status {
        /** Satisfied — possibly with a recorded, non-blocking finding (e.g. cosmetic). */
        MET,
        /** Not satisfied — carries a blocking finding. */
        NOT_MET,
        /** Could not be evaluated with sufficient confidence. Fails closed. */
        INDETERMINATE
    }
}
