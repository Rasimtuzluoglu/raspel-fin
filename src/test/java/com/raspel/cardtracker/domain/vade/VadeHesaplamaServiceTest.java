package com.raspel.cardtracker.domain.vade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VadeHesaplamaServiceTest {

    private final VadeHesaplamaService service = new VadeHesaplamaService();

    @Test
    void parseCSV_shouldParseValidCsv() throws Exception {
        String csv = "Tutar,Vade Tarihi\n1000.00,2026-12-31\n2000.50,2026-06-15\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<VadeDTO> items = service.parseCSV(is);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getTutar()).isEqualByComparingTo("1000.00");
        assertThat(items.get(0).getVadeTarihi()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(items.get(1).getTutar()).isEqualByComparingTo("2000.50");
    }

    @Test
    void parseCSV_shouldHandleSemicolonSeparator() throws Exception {
        String csv = "Tutar;Vade Tarihi\n500.00;2026-07-01\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<VadeDTO> items = service.parseCSV(is);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getTutar()).isEqualByComparingTo("500.00");
    }

    @Test
    void parseCSV_shouldHandleTurkishDecimalSeparator() throws Exception {
        String csv = "Tutar;Vade Tarihi\n1500.75;2026-08-15\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<VadeDTO> items = service.parseCSV(is);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getTutar()).isEqualByComparingTo("1500.75");
    }

    @Test
    void parseCSV_shouldSkipZeroAndNegativeAmounts() throws Exception {
        String csv = "Tutar,Vade Tarihi\n0.00,2026-12-31\n-100.00,2026-06-15\n500.00,2026-07-01\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<VadeDTO> items = service.parseCSV(is);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getTutar()).isEqualByComparingTo("500.00");
    }

    @Test
    void parseCSV_shouldSkipInvalidDates() throws Exception {
        String csv = "Tutar,Vade Tarihi\n100.00,gecersiz-tarih\n200.00,2026-06-15\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<VadeDTO> items = service.parseCSV(is);

        assertThat(items).hasSize(1);
    }

    @Test
    void parseCSV_shouldThrowWhenHeaderOnly() {
        String csv = "Tutar,Vade Tarihi\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.parseCSV(is))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("başlık");
    }

    @Test
    void parseCSV_shouldThrowWhenNoValidRows() {
        String csv = "Tutar,Vade Tarihi\n-1.00,gecersiz\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.parseCSV(is))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("geçerli satır");
    }

    @Test
    void parseCSV_shouldStripCurrencySymbols() throws Exception {
        String csv = "Tutar;Vade Tarihi\n₺1000;2026-12-31\n500TL;2026-06-15\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<VadeDTO> items = service.parseCSV(is);

        assertThat(items).hasSize(2);
    }

    @Test
    void calculateWeightedDays_shouldReturnCorrectAverage() {
        LocalDate today = LocalDate.now();
        VadeDTO item1 = new VadeDTO(new BigDecimal("1000.00"), today.plusDays(30));
        VadeDTO item2 = new VadeDTO(new BigDecimal("2000.00"), today.plusDays(60));
        List<VadeDTO> items = List.of(item1, item2);

        BigDecimal result = service.calculateWeightedDays(items);

        assertThat(result).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void calculateWeightedDays_shouldReturnZeroForEmptyList() {
        BigDecimal result = service.calculateWeightedDays(List.of());

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateWeightedDays_shouldReturnZeroWhenTotalAmountIsZero() {
        VadeDTO item = new VadeDTO(BigDecimal.ZERO, LocalDate.now().plusDays(30));
        BigDecimal result = service.calculateWeightedDays(List.of(item));

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateAverageDate_shouldReturnTodayPlusAvgDays() {
        LocalDate today = LocalDate.now();
        LocalDate result = service.calculateAverageDate(new BigDecimal("10"));

        assertThat(result).isEqualTo(today.plusDays(10));
    }

    @Test
    void parseCSV_shouldSkipEmptyLines() throws Exception {
        String csv = "Tutar,Vade Tarihi\n\n100.00,2026-06-15\n\n200.00,2026-07-01\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<VadeDTO> items = service.parseCSV(is);

        assertThat(items).hasSize(2);
    }
}
