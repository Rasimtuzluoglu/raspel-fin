package com.raspel.cardtracker.domain.report;

import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.domain.cheque.ChequeService;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.domain.settings.AppSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyReportServiceTest {

    @Mock private ExpenseService expenseService;
    @Mock private CardService cardService;
    @Mock private ChequeService chequeService;
    @Mock private AppSettingsService appSettingsService;

    @InjectMocks
    private MonthlyReportService monthlyReportService;

    @Test
    void generateMonthlyReport_shouldReturnNonEmptyInputStream() {
        when(cardService.findAllActive()).thenReturn(List.of());
        when(expenseService.getInstallmentsForMonth(2026, 6)).thenReturn(List.of());
        when(expenseService.getCardTotalsForMonth(2026, 6, List.of())).thenReturn(Map.of());
        when(chequeService.findAll()).thenReturn(List.of());
        when(appSettingsService.getCompanyName()).thenReturn("Test Şirket");

        ByteArrayInputStream result = monthlyReportService.generateMonthlyReport(2026, 6);

        assertThat(result).isNotNull();
        assertThat(result.available()).isGreaterThan(0);
    }

    @Test
    void generateMonthlyReport_shouldHandleCurrentMonth() {
        when(cardService.findAllActive()).thenReturn(List.of());
        when(expenseService.getInstallmentsForMonth(anyInt(), anyInt())).thenReturn(List.of());
        when(expenseService.getCardTotalsForMonth(anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyList())).thenReturn(Map.of());
        when(chequeService.findAll()).thenReturn(List.of());
        when(appSettingsService.getCompanyName()).thenReturn("Test");

        ByteArrayInputStream result = monthlyReportService.generateMonthlyReport(2026, 1);

        assertThat(result).isNotNull();
    }
}
