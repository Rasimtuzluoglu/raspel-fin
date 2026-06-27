package com.raspel.cardtracker.domain.budget;

import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import com.raspel.cardtracker.domain.department.Department;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private BudgetService budgetService;

    @Test
    void save_shouldThrowWhenLimitNegative() {
        Budget budget = Budget.builder()
                .limitAmount(new BigDecimal("-100"))
                .build();

        assertThatThrownBy(() -> budgetService.save(budget))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0'dan küçük olamaz");
    }

    @Test
    void save_shouldCreateNewBudget() {
        Department dept = new Department();
        dept.setId(1L);
        dept.setName("Test");

        Budget budget = Budget.builder()
                .department(dept)
                .year(2026)
                .month(6)
                .limitAmount(new BigDecimal("10000"))
                .build();

        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> {
            Budget b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        Budget saved = budgetService.save(budget);

        assertThat(saved.getId()).isEqualTo(1L);
        verify(budgetRepository).save(budget);
        verify(auditLogService).log(any(), eq("Bütçe"), any(), contains("oluşturuldu"));
    }

    @Test
    void save_shouldUpdateExistingBudget() {
        Budget budget = Budget.builder()
                .id(5L)
                .limitAmount(new BigDecimal("20000"))
                .build();

        when(budgetRepository.save(any(Budget.class))).thenReturn(budget);

        budgetService.save(budget);

        verify(auditLogService).log(any(), eq("Bütçe"), any(), contains("güncellendi"));
    }

    @Test
    void delete_shouldDeleteAndLog() {
        Budget budget = Budget.builder()
                .id(1L)
                .department(new Department())
                .year(2026)
                .month(6)
                .limitAmount(BigDecimal.ZERO)
                .build();

        when(budgetRepository.findById(1L)).thenReturn(Optional.of(budget));

        budgetService.delete(1L);

        verify(auditLogService).log(any(), eq("Bütçe"), eq(1L), contains("Bütçe silindi"));
        verify(budgetRepository).deleteById(1L);
    }

    @Test
    void findByYearAndMonth_shouldReturnList() {
        List<Budget> budgets = List.of(new Budget(), new Budget());
        when(budgetRepository.findByYearAndMonth(2026, 6)).thenReturn(budgets);

        List<Budget> result = budgetService.findByYearAndMonth(2026, 6);

        assertThat(result).hasSize(2);
    }

    @Test
    void findByDepartmentAndYearAndMonth_shouldReturnOptional() {
        Budget budget = Budget.builder()
                .id(1L)
                .year(2026)
                .month(6)
                .build();

        when(budgetRepository.findByDepartmentIdAndYearAndMonth(1L, 2026, 6))
                .thenReturn(Optional.of(budget));

        Optional<Budget> result = budgetService.findByDepartmentAndYearAndMonth(1L, 2026, 6);

        assertThat(result).isPresent();
    }

    @Test
    void findAll_shouldReturnAll() {
        when(budgetRepository.findAll()).thenReturn(List.of(new Budget(), new Budget()));

        assertThat(budgetService.findAll()).hasSize(2);
    }
}
