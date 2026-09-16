package com.sc.verdict.examination;

import com.sc.verdict.shared.Ids.ObligationId;
import com.sc.verdict.shared.Ids.PartyId;
import com.sc.verdict.shared.Ids.PayeeId;
import com.sc.verdict.shared.Money;

import java.util.Objects;

/**
 * One payee's share of a determination: pay {@code amount} to {@code payeeParty} against
 * {@code obligation}. This is part of the determination contract the money plane consumes; it names
 * only ids and money, never an obligation, condition or finding type, so the ledger can act on it
 * without importing the decision plane's vocabulary (ADR-001).
 */
public record DisbursementLine(ObligationId obligation, PayeeId payee, PartyId payeeParty, Money amount) {

    public DisbursementLine {
        Objects.requireNonNull(obligation, "obligation");
        Objects.requireNonNull(payee, "payee");
        Objects.requireNonNull(payeeParty, "payeeParty");
        Objects.requireNonNull(amount, "amount");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("disbursement amount must not be negative");
        }
    }
}
