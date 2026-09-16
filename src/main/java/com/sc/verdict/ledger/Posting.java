package com.sc.verdict.ledger;

import com.sc.verdict.shared.Ids.PostingId;
import com.sc.verdict.shared.Money;

import java.time.Instant;
import java.util.Objects;

/**
 * One immutable double-entry movement: {@code amount} leaves {@code debit} and enters {@code credit}.
 *
 * <p>Postings are never amended (NFR: ledger integrity). A correction is a new, opposite posting, so
 * the history is the truth. {@code correlationKey} carries the instruction or determination id that
 * caused the movement, which is how a posting is traced back to its evidence.
 */
public record Posting(
        PostingId id,
        String debit,
        String credit,
        Money amount,
        String narrative,
        String correlationKey,
        Instant at) {

    public Posting {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(debit, "debit");
        Objects.requireNonNull(credit, "credit");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(narrative, "narrative");
        Objects.requireNonNull(correlationKey, "correlationKey");
        Objects.requireNonNull(at, "at");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("posting amount must not be negative; reverse the legs instead");
        }
    }
}
