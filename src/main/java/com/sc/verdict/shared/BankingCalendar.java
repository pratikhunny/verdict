package com.sc.verdict.shared;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Banking-day arithmetic for the approval response window. Under documentary-credit practice the
 * response window runs in banking days, and a bank that misses it loses the right to refuse (UCP 600
 * art. 16(f)); the clock is a liability control, not a courtesy. Weekends are excluded; a real
 * calendar would also exclude the relevant centre's holidays — deferred, and behind this one method.
 */
public final class BankingCalendar {

    private BankingCalendar() {}

    /** The date that is {@code bankingDays} banking days on or after {@code start}. */
    public static LocalDate addBankingDays(LocalDate start, int bankingDays) {
        if (bankingDays < 0) {
            throw new IllegalArgumentException("bankingDays must not be negative");
        }
        LocalDate date = start;
        int remaining = bankingDays;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (isBankingDay(date)) {
                remaining--;
            }
        }
        return date;
    }

    public static boolean isBankingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }
}
