package com.sc.verdict.ledger;

import com.sc.verdict.examination.Determination;
import com.sc.verdict.examination.DisbursementLine;
import com.sc.verdict.examination.Outcome;
import com.sc.verdict.examination.ResidualLine;
import com.sc.verdict.shared.Ids.DeterminationId;
import com.sc.verdict.shared.Ids.ObligationId;
import com.sc.verdict.shared.Ids.PayeeId;
import com.sc.verdict.shared.Ids.PostingId;
import com.sc.verdict.shared.Money;

import java.time.Instant;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * L6 — the fund control ledger. Double-entry, with earmarks as internal reservations of held funds,
 * and an invariant that no ring-fenced account goes negative (no over-earmark, no over-disbursement).
 * It is never mocked, even in a hackathon (HLD §9), because the reconciliation control that justifies
 * the whole three-plane split has nothing to check against if the money side is a stub.
 *
 * <p>It consumes a {@link Determination} and reads only its outcome and its money lines. It imports
 * no obligation, condition or finding type — the determination is the entire contract from the
 * decision plane (ADR-001). Applying a determination is idempotent on its id, so a re-delivered or
 * replayed determination moves nothing twice (NFR: idempotency).
 */
public final class FundControlLedger {

    static final String EXTERNAL_FUNDING = "EXTERNAL/FUNDING";
    static final String UNALLOCATED = "ESCROW/UNALLOCATED";
    private static final String EARMARK_PREFIX = "ESCROW/EARMARK:";
    private static final String PAYEE_PREFIX = "EXTERNAL/PAYEE:";

    private final Currency currency;
    private final Map<String, Money> balances = new LinkedHashMap<>();
    private final List<Posting> postings = new java.util.ArrayList<>();
    private final java.util.Set<DeterminationId> applied = new java.util.HashSet<>();
    private int postingSeq = 0;

    public FundControlLedger(Currency currency) {
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    // ---------------------------------------------------------------- setup movements

    /** Bring external funds into the ring-fenced balance. */
    public void fund(Money amount, String correlationKey, Instant at) {
        requireCurrency(amount);
        post(EXTERNAL_FUNDING, UNALLOCATED, amount, "fund escrow", correlationKey, at);
    }

    /** Reserve part of the unallocated balance against an obligation. */
    public void earmark(ObligationId obligation, Money amount, String correlationKey, Instant at) {
        requireCurrency(amount);
        post(UNALLOCATED, earmarkAccount(obligation), amount,
                "earmark " + obligation, correlationKey, at);
    }

    // ---------------------------------------------------------------- the decision → money boundary

    /**
     * Apply a determination. RELEASE disburses each earmark in full; PARTIAL_RELEASE disburses the
     * pro-rata share and reclassifies the remainder into a residual earmark; HOLD and
     * HOLD_PENDING_APPROVAL move nothing — the earmark stays exactly as it was.
     */
    public void apply(Determination determination) {
        Objects.requireNonNull(determination, "determination");
        if (!applied.add(determination.id())) {
            return; // idempotent: already applied
        }
        String key = determination.id().value();
        Instant at = determination.effectiveAt();

        if (determination.outcome() == Outcome.RELEASE || determination.outcome() == Outcome.PARTIAL_RELEASE) {
            for (DisbursementLine line : determination.disbursements()) {
                post(earmarkAccount(line.obligation()), payeeAccount(line.payee()), line.amount(),
                        "disburse to " + line.payeeParty(), key, at);
            }
            for (ResidualLine line : determination.residuals()) {
                post(earmarkAccount(line.sourceObligation()), earmarkAccount(line.residualObligation()),
                        line.retained(), "re-earmark residual " + line.residualObligation(), key, at);
            }
        }
        // HOLD / HOLD_PENDING_APPROVAL: deliberately no movement.
    }

    // ---------------------------------------------------------------- queries

    public Money heldBalance() {
        return sumWhere(account -> account.startsWith("ESCROW/"));
    }

    public Money reservedTotal() {
        return sumWhere(account -> account.startsWith(EARMARK_PREFIX));
    }

    public Money unallocated() {
        return balances.getOrDefault(UNALLOCATED, Money.zero(currency));
    }

    public Money earmarkFor(ObligationId obligation) {
        return balances.getOrDefault(earmarkAccount(obligation), Money.zero(currency));
    }

    public Money disbursedTotal() {
        return sumWhere(account -> account.startsWith(PAYEE_PREFIX));
    }

    /** Active earmarks, obligation → reserved amount, excluding those drained to zero. */
    public Map<ObligationId, Money> activeEarmarks() {
        var out = new LinkedHashMap<ObligationId, Money>();
        for (var e : balances.entrySet()) {
            if (e.getKey().startsWith(EARMARK_PREFIX) && !e.getValue().isZero()) {
                out.put(new ObligationId(e.getKey().substring(EARMARK_PREFIX.length())), e.getValue());
            }
        }
        return out;
    }

    public List<Posting> postings() {
        return List.copyOf(postings);
    }

    // ---------------------------------------------------------------- internals

    private void post(String debit, String credit, Money amount, String narrative, String key, Instant at) {
        Money newDebit = balances.getOrDefault(debit, Money.zero(currency)).minus(amount);
        Money newCredit = balances.getOrDefault(credit, Money.zero(currency)).plus(amount);
        // Ring-fenced accounts may never go negative: that would be an over-earmark or over-disbursement.
        if (debit.startsWith("ESCROW/") && newDebit.isNegative()) {
            throw new LedgerInvariantException(
                    "posting would drive %s negative: %s".formatted(debit, newDebit));
        }
        balances.put(debit, newDebit);
        balances.put(credit, newCredit);
        postings.add(new Posting(new PostingId("PST-%04d".formatted(++postingSeq)),
                debit, credit, amount, narrative, key, at));
    }

    private Money sumWhere(java.util.function.Predicate<String> accountFilter) {
        Money total = Money.zero(currency);
        for (var e : balances.entrySet()) {
            if (accountFilter.test(e.getKey())) {
                total = total.plus(e.getValue());
            }
        }
        return total;
    }

    private void requireCurrency(Money amount) {
        if (!amount.currency().equals(currency)) {
            throw new IllegalArgumentException("ledger is " + currency.getCurrencyCode() + ", got " + amount);
        }
    }

    private static String earmarkAccount(ObligationId obligation) {
        return EARMARK_PREFIX + obligation.value();
    }

    private static String payeeAccount(PayeeId payee) {
        return PAYEE_PREFIX + payee.value();
    }

    /** Signals a violated ledger invariant — the money plane refuses rather than corrupts. */
    public static final class LedgerInvariantException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public LedgerInvariantException(String message) { super(message); }
    }
}
