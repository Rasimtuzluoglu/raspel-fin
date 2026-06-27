package com.raspel.cardtracker.domain.expense;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TcmbCurrencyServiceTest {

    private final TcmbCurrencyService service = new TcmbCurrencyService();

    @Test
    void getExchangeRate_shouldReturnOneForTRY() {
        BigDecimal rate = service.getExchangeRate("TRY", java.time.LocalDate.now());

        assertThat(rate).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void getExchangeRate_shouldReturnOneForTry() {
        BigDecimal rate = service.getExchangeRate("try", java.time.LocalDate.now());

        assertThat(rate).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void getExchangeRate_shouldReturnOneForNull() {
        BigDecimal rate = service.getExchangeRate(null, java.time.LocalDate.now());

        assertThat(rate).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void getExchangeRate_shouldFallbackForUnknownCurrency() {
        BigDecimal rate = service.getExchangeRate("XYZ", java.time.LocalDate.now());

        assertThat(rate).isNotNull();
    }

    @Test
    void getExchangeRate_shouldUseTodayForFutureDates() {
        java.time.LocalDate futureDate = java.time.LocalDate.now().plusDays(30);

        BigDecimal rate = service.getExchangeRate("USD", futureDate);

        assertThat(rate).isNotNull();
    }
}
