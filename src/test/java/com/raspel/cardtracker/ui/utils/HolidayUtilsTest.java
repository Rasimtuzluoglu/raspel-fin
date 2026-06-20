package com.raspel.cardtracker.ui.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HolidayUtilsTest {

    @Test
    void getNextBusinessDay_regularWeekdayReturnsSameDate() {
        LocalDate tuesday = LocalDate.of(2026, 1, 13);
        assertThat(HolidayUtils.getNextBusinessDay(tuesday)).isEqualTo(tuesday);
    }

    @Test
    void getNextBusinessDay_saturdayReturnsMonday() {
        LocalDate saturday = LocalDate.of(2026, 1, 17);
        LocalDate monday = LocalDate.of(2026, 1, 19);
        assertThat(HolidayUtils.getNextBusinessDay(saturday)).isEqualTo(monday);
    }

    @Test
    void getNextBusinessDay_sundayReturnsMonday() {
        LocalDate sunday = LocalDate.of(2026, 1, 18);
        LocalDate monday = LocalDate.of(2026, 1, 19);
        assertThat(HolidayUtils.getNextBusinessDay(sunday)).isEqualTo(monday);
    }

    @Test
    void getNextBusinessDay_knownHolidayReturnsNextWorkday() {
        // 2026-01-01 is Thursday (New Year's) → next workday is Friday 2026-01-02
        LocalDate newYear = LocalDate.of(2026, 1, 1);
        LocalDate nextWorkday = LocalDate.of(2026, 1, 2);
        assertThat(HolidayUtils.getNextBusinessDay(newYear)).isEqualTo(nextWorkday);
    }

    @Test
    void getNextBusinessDay_holidayOnFridayReturnsMonday() {
        // 2026-05-01 is Friday (Labor Day) → next workday is Monday 2026-05-04
        LocalDate laborDay = LocalDate.of(2026, 5, 1);
        LocalDate monday = LocalDate.of(2026, 5, 4);
        assertThat(HolidayUtils.getNextBusinessDay(laborDay)).isEqualTo(monday);
    }

    @Test
    void getNextBusinessDay_consecutiveReligiousHolidaysSkipToNextWorkday() {
        // 2026-03-19, 03-20, 03-21 are all Ramazan Bayramı holidays
        // 2026-03-21 is Saturday, 03-22 is Sunday → next workday is 2026-03-23 (Monday)
        LocalDate ramazanDay1 = LocalDate.of(2026, 3, 19);
        LocalDate nextWorkday = LocalDate.of(2026, 3, 23);
        assertThat(HolidayUtils.getNextBusinessDay(ramazanDay1)).isEqualTo(nextWorkday);
    }

    @Test
    void getNextBusinessDay_nullReturnsNull() {
        assertThat(HolidayUtils.getNextBusinessDay(null)).isNull();
    }

    @Test
    void isWeekendOrHoliday_returnsTrueForSaturday() {
        assertThat(HolidayUtils.isWeekendOrHoliday(LocalDate.of(2026, 1, 17))).isTrue();
    }

    @Test
    void isWeekendOrHoliday_returnsTrueForSunday() {
        assertThat(HolidayUtils.isWeekendOrHoliday(LocalDate.of(2026, 1, 18))).isTrue();
    }

    @Test
    void isWeekendOrHoliday_returnsTrueForKnownHoliday() {
        assertThat(HolidayUtils.isWeekendOrHoliday(LocalDate.of(2026, 1, 1))).isTrue();
    }

    @Test
    void isWeekendOrHoliday_returnsFalseForRegularWorkday() {
        assertThat(HolidayUtils.isWeekendOrHoliday(LocalDate.of(2026, 1, 13))).isFalse();
    }
}
