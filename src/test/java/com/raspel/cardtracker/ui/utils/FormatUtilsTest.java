package com.raspel.cardtracker.ui.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FormatUtilsTest {

    @Test
    void parseTurkishCurrency_withTurkishFormatReturnsCorrectBigDecimal() {
        BigDecimal result = FormatUtils.parseTurkishCurrency("1.000,50");
        assertThat(result).isEqualByComparingTo("1000.50");
    }

    @Test
    void parseTurkishCurrency_withPlainNumberReturnsCorrectBigDecimal() {
        BigDecimal result = FormatUtils.parseTurkishCurrency("500");
        assertThat(result).isEqualByComparingTo("500");
    }

    @Test
    void parseTurkishCurrency_withEnglishFormatReturnsCorrectBigDecimal() {
        BigDecimal result = FormatUtils.parseTurkishCurrency("1000.50");
        assertThat(result).isEqualByComparingTo("1000.50");
    }

    @Test
    void parseTurkishCurrency_withOnlyCommaReturnsCorrectBigDecimal() {
        BigDecimal result = FormatUtils.parseTurkishCurrency("1234,56");
        assertThat(result).isEqualByComparingTo("1234.56");
    }

    @Test
    void parseTurkishCurrency_withNullReturnsZero() {
        BigDecimal result = FormatUtils.parseTurkishCurrency(null);
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseTurkishCurrency_withEmptyStringReturnsZero() {
        BigDecimal result = FormatUtils.parseTurkishCurrency("");
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseTurkishCurrency_withWhitespaceReturnsZero() {
        BigDecimal result = FormatUtils.parseTurkishCurrency("   ");
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void formatTurkishCurrency_withValueReturnsTurkishFormattedString() {
        String result = FormatUtils.formatTurkishCurrency(new BigDecimal("1000.50"));
        assertThat(result).isEqualTo("1.000,50");
    }

    @Test
    void formatTurkishCurrency_withIntegerValueReturnsWithDecimals() {
        String result = FormatUtils.formatTurkishCurrency(new BigDecimal("5000"));
        assertThat(result).isEqualTo("5.000,00");
    }

    @Test
    void formatTurkishCurrency_withNullReturnsZeroFormatted() {
        String result = FormatUtils.formatTurkishCurrency(null);
        assertThat(result).isEqualTo("0,00");
    }
}
