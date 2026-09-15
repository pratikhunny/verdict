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

    public boolean isNegative() {
        return amount.signum() < 0;
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
