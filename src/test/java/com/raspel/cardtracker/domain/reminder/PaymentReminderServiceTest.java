package com.raspel.cardtracker.domain.reminder;

import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.cheque.Cheque;
import com.raspel.cardtracker.domain.cheque.ChequeRepository;
import com.raspel.cardtracker.domain.cheque.ChequeStatus;
import com.raspel.cardtracker.domain.expense.Expense;
import com.raspel.cardtracker.domain.expense.InstallmentEntry;
import com.raspel.cardtracker.domain.expense.InstallmentEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReminderServiceTest {

    @Mock private InstallmentEntryRepository installmentEntryRepository;
    @Mock private ChequeRepository chequeRepository;

    @InjectMocks
    private PaymentReminderService paymentReminderService;

    @Test
    void getReminderSummary_shouldReturnCorrectCounts() {
        PaymentReminderService.ReminderSummary summary = paymentReminderService.getReminderSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalCount()).isEqualTo(0);
        assertThat(summary.getCriticalCount()).isEqualTo(0);
    }

    @Test
    void getOverdueInstallments_shouldFilterCorrectly() {
        when(installmentEntryRepository.findAllUnpaidWithDetails()).thenReturn(List.of());

        List<InstallmentEntry> result = paymentReminderService.getOverdueInstallments();

        assertThat(result).isEmpty();
    }

    @Test
    void getUpcomingInstallments_shouldFilterCorrectly() {
        when(installmentEntryRepository.findAllUnpaidWithDetails()).thenReturn(List.of());

        List<InstallmentEntry> result = paymentReminderService.getUpcomingInstallments(7);

        assertThat(result).isEmpty();
    }

    @Test
    void getOverdueInstallments_shouldReturnUnpaidBeforeToday() {
        Card card = createCard();
        Expense expense = createExpense(card);
        InstallmentEntry entry = InstallmentEntry.builder()
                .expense(expense)
                .dueYear(2025)
                .dueMonth(1)
                .amount(new BigDecimal("500"))
                .isPaid(false)
                .build();

        when(installmentEntryRepository.findAllUnpaidWithDetails()).thenReturn(List.of(entry));

        List<InstallmentEntry> result = paymentReminderService.getOverdueInstallments();

        assertThat(result).hasSize(1);
    }

    @Test
    void getUpcomingInstallments_shouldReturnWithinDays() {
        Card card = createCard();
        Expense expense = createExpense(card);
        LocalDate today = LocalDate.now();
        YearMonth futureYm = YearMonth.from(today).plusMonths(1);
        InstallmentEntry entry = InstallmentEntry.builder()
                .expense(expense)
                .dueYear(futureYm.getYear())
                .dueMonth(futureYm.getMonthValue())
                .amount(new BigDecimal("300"))
                .isPaid(false)
                .build();

        when(installmentEntryRepository.findAllUnpaidWithDetails()).thenReturn(List.of(entry));

        List<InstallmentEntry> result = paymentReminderService.getUpcomingInstallments(90);

        assertThat(result).isNotEmpty();
    }

    @Test
    void getActiveCheques_shouldReturnPortfolioCheques() {
        Cheque cheque = new Cheque();
        cheque.setStatus(ChequeStatus.PORTFOLIO);
        cheque.setMaturityDate(LocalDate.now().plusDays(10));

        when(chequeRepository.findAllByOrderByMaturityDateAsc()).thenReturn(List.of(cheque));

        List<Cheque> result = paymentReminderService.getActiveCheques();

        assertThat(result).hasSize(1);
    }

    private Card createCard() {
        Card card = new Card();
        card.setId(1L);
        card.setClosingDay(15);
        card.setDueDay(10);
        card.setActive(true);
        return card;
    }

    private Expense createExpense(Card card) {
        Expense expense = new Expense();
        expense.setId(1L);
        expense.setCard(card);
        expense.setInstallments(1);
        expense.setDescription("Test");
        return expense;
    }
}
