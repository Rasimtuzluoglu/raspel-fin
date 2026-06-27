package com.raspel.cardtracker.domain.expense;

import com.raspel.cardtracker.domain.card.CardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BankStatementImportServiceTest {

    @Mock private ExpenseService expenseService;
    @Mock private CardRepository cardRepository;

    @Test
    void parseCsv_shouldParseValidCsv() {
        BankStatementImportService service = new BankStatementImportService(expenseService, cardRepository);

        String csv = "Tarih;Açıklama;Tutar\n" +
                "15.06.2026;Market Alışverişi;-150,50\n" +
                "16.06.2026;Akaryakıt;-500,00\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<BankStatementImportService.ImportRow> rows = service.parseCsv(inputStream);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).description()).contains("Market");
        assertThat(rows.get(0).isExpense()).isTrue();
    }

    @Test
    void parseCsv_shouldHandleEmptyFile() {
        BankStatementImportService service = new BankStatementImportService(expenseService, cardRepository);

        String csv = "Tarih;Açıklama;Tutar\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<BankStatementImportService.ImportRow> rows = service.parseCsv(inputStream);

        assertThat(rows).isEmpty();
    }

    @Test
    void parseCsv_shouldHandleCommaDelimiter() {
        BankStatementImportService service = new BankStatementImportService(expenseService, cardRepository);

        String csv = "date,description,amount\n" +
                "15.06.2026,Test Payment,-200,00\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<BankStatementImportService.ImportRow> rows = service.parseCsv(inputStream);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).amount().compareTo(new java.math.BigDecimal("200.00"))).isEqualTo(0);
    }

    @Test
    void parseCsv_shouldParseDifferentDateFormat() {
        BankStatementImportService service = new BankStatementImportService(expenseService, cardRepository);

        String csv = "Tarih;Açıklama;Tutar\n" +
                "2026-06-15;Online Ödeme;-750,00\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        List<BankStatementImportService.ImportRow> rows = service.parseCsv(inputStream);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).date()).isEqualTo("2026-06-15");
    }
}
