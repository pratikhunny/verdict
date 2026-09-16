package com.sc.verdict.shared;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * A currency-tagged monetary amount.
 *
 * <p>Deliberately refuses arithmetic or comparison across currencies: in escrow, a silent
 * USD/MYR comparison is a control failure, not a rounding bug. Scale is normalised to the
 * currency's minor-unit digits on construction so that {@code USD 50} and {@code USD 50.00}
 * compare equal.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(currency.getDefaultFractionDigits(), java.math.RoundingMode.UNNECESSARY);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    /**
     * Scale by a dimensionless factor (a percentage or a pro-rata ratio), rounding to the
     * currency's minor unit. Rounding is {@code HALF_UP}; callers that split one amount across
     * several payees must reconcile the rounding remainder rather than trust the parts to sum
     * (see the ledger's residual computation, which derives the retained amount by subtraction).
     */
    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor");
        BigDecimal scaled = amount.multiply(factor)
                .setScale(currency.getDefaultFractionDigits(), java.math.RoundingMode.HALF_UP);
        return new Money(scaled, currency);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    /**
     * @throws CurrencyMismatchException if the two amounts are denominated differently.
     */
    @Override
    public int compareTo(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }

    /** Signals an attempt to compare or combine amounts in different currencies. */
    public static final class CurrencyMismatchException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public CurrencyMismatchException(Currency left, Currency right) {
            super("Currency mismatch: %s vs %s".formatted(left.getCurrencyCode(), right.getCurrencyCode()));
        }
    }
}
