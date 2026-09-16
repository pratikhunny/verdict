package com.sc.verdict.examination;

import com.sc.verdict.shared.Ids.ObligationId;
import com.sc.verdict.shared.Ids.PayeeId;
import com.sc.verdict.shared.Money;

import java.util.Objects;

/**
 * The un-released remainder of a partial release, to be re-earmarked as a residual obligation.
 * {@code residualObligation} is the id the money plane earmarks against and the decision plane
 * re-examines later; {@code retained} is what stays held. Like {@link DisbursementLine}, it carries
 * only ids and money.
 */
public record ResidualLine(ObligationId sourceObligation, ObligationId residualObligation,
                           PayeeId payee, Money retained) {

    public ResidualLine {
        Objects.requireNonNull(sourceObligation, "sourceObligation");
        Objects.requireNonNull(residualObligation, "residualObligation");
        Objects.requireNonNull(payee, "payee");
        Objects.requireNonNull(retained, "retained");
        if (retained.isNegative()) {
            throw new IllegalArgumentException("retained amount must not be negative");
        }
    }
}
