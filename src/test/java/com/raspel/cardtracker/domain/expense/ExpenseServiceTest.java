package com.raspel.cardtracker.domain.expense;

import com.raspel.cardtracker.domain.audit.AuditLogService;
import com.raspel.cardtracker.domain.card.Card;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private InstallmentEntryRepository installmentEntryRepository;

    @Mock
    private TcmbCurrencyService tcmbCurrencyService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ExpenseService expenseService;

    @Test
    void generateInstallments_shouldSplitAmountEvenlyAndDistributeRemainderToLast() throws Exception {
        Expense expense = Expense.builder()
                .totalAmount(new BigDecimal("1000.00"))
                .installments(12)
                .expenseDate(LocalDate.of(2026, 1, 15))
                .description("Test")
                .build();

        Method method = ExpenseService.class.getDeclaredMethod("generateInstallments", Expense.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<InstallmentEntry> entries = (List<InstallmentEntry>) method.invoke(expenseService, expense);

        assertThat(entries).hasSize(12);

        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("83.33");
        assertThat(entries.get(0).getDueYear()).isEqualTo(2026);
        assertThat(entries.get(0).getDueMonth()).isEqualTo(2);

        assertThat(entries.get(10).getAmount()).isEqualByComparingTo("83.33");
        assertThat(entries.get(10).getDueYear()).isEqualTo(2026);
        assertThat(entries.get(10).getDueMonth()).isEqualTo(12);

        assertThat(entries.get(11).getAmount()).isEqualByComparingTo("83.37");
        assertThat(entries.get(11).getDueYear()).isEqualTo(2027);
        assertThat(entries.get(11).getDueMonth()).isEqualTo(1);

        BigDecimal sum = entries.stream().map(InstallmentEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1000.00");
    }

    @Test
    void generateInstallments_shouldHandleExactDivision() throws Exception {
        Expense expense = Expense.builder()
                .totalAmount(new BigDecimal("1200.00"))
                .installments(3)
                .expenseDate(LocalDate.of(2026, 3, 1))
                .description("Test")
                .build();

        Method method = ExpenseService.class.getDeclaredMethod("generateInstallments", Expense.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<InstallmentEntry> entries = (List<InstallmentEntry>) method.invoke(expenseService, expense);

        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("400.00");
        assertThat(entries.get(1).getAmount()).isEqualByComparingTo("400.00");
        assertThat(entries.get(2).getAmount()).isEqualByComparingTo("400.00");
        assertThat(entries.get(0).getDueMonth()).isEqualTo(4);
        assertThat(entries.get(1).getDueMonth()).isEqualTo(5);
        assertThat(entries.get(2).getDueMonth()).isEqualTo(6);
    }

    @Test
    void generateInstallments_shouldThrowWhenInstallmentCountIsZero() throws Exception {
        Expense expense = Expense.builder()
                .totalAmount(new BigDecimal("1000.00"))
                .installments(0)
                .expenseDate(LocalDate.of(2026, 1, 15))
                .description("Test")
                .build();

        Method method = ExpenseService.class.getDeclaredMethod("generateInstallments", Expense.class);
        method.setAccessible(true);

        Throwable thrown = catchThrowable(() -> method.invoke(expenseService, expense));
        assertThat(thrown).isInstanceOf(InvocationTargetException.class);
        assertThat(thrown.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Taksit sayısı en az 1 olmalıdır");
    }

    @Test
    void createExpense_shouldThrowWhenExpenseDateIsNull() {
        Expense expense = Expense.builder()
                .totalAmount(new BigDecimal("500.00"))
                .installments(1)
                .build();

        assertThatThrownBy(() -> expenseService.createExpense(expense))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Harcama tarihi zorunludur");
    }

    @Test
    void createExpense_shouldThrowWhenAmountIsNegative() {
        Card card = new Card(); card.setId(1L);
        Expense expense = Expense.builder()
                .card(card)
                .expenseDate(LocalDate.of(2026, 1, 15))
                .originalAmount(new BigDecimal("-100.00"))
                .installments(1)
                .build();

        assertThatThrownBy(() -> expenseService.createExpense(expense))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tutar 0'dan büyük olmalıdır");
    }

    @Test
    void createExpense_shouldThrowWhenAmountIsZero() {
        Card card = new Card(); card.setId(1L);
        Expense expense = Expense.builder()
                .card(card)
                .expenseDate(LocalDate.of(2026, 1, 15))
                .originalAmount(BigDecimal.ZERO)
                .installments(1)
                .build();

        assertThatThrownBy(() -> expenseService.createExpense(expense))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tutar 0'dan büyük olmalıdır");
    }

    @Test
    void createExpense_shouldThrowWhenBothOriginalAndTotalAmountAreNull() {
        Card card = new Card(); card.setId(1L);
        Expense expense = Expense.builder()
                .card(card)
                .expenseDate(LocalDate.of(2026, 1, 15))
                .installments(1)
                .build();

        assertThatThrownBy(() -> expenseService.createExpense(expense))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tutar belirtilmelidir");
    }

    @Test
    void createExpense_shouldSucceedWithValidData() {
        Card card = new Card();
        card.setId(1L);
        Expense expense = Expense.builder()
                .card(card)
                .expenseDate(LocalDate.of(2026, 1, 15))
                .originalAmount(new BigDecimal("500.00"))
                .installments(1)
                .currency("TRY")
                .description("Test expense")
                .build();

        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        when(installmentEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        expenseService.createExpense(expense);

        verify(expenseRepository).save(any(Expense.class));
        verify(installmentEntryRepository).saveAll(any());
    }

    @Test
    void getUnpaidBalance_shouldReturnSumFromRepository() {
        Long cardId = 1L;
        BigDecimal expectedBalance = new BigDecimal("1500.75");
        when(installmentEntryRepository.sumUnpaidAmountByCardId(cardId)).thenReturn(expectedBalance);

        BigDecimal result = expenseService.getUnpaidBalance(cardId);

        assertThat(result).isEqualByComparingTo(expectedBalance);
        verify(installmentEntryRepository).sumUnpaidAmountByCardId(cardId);
    }
}
