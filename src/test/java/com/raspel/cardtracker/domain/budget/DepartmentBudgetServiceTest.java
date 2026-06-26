package com.raspel.cardtracker.domain.budget;

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
class DepartmentBudgetServiceTest {

    @Mock private DepartmentBudgetRepository repository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private DepartmentBudgetService service;

    @Test
    void save_shouldThrowWhenBudgetLimitIsNegative() {
        DepartmentBudget budget = new DepartmentBudget();
        budget.setBudgetLimit(new BigDecimal("-100.00"));

        assertThatThrownBy(() -> service.save(budget))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bütçe limiti negatif olamaz");
    }

    @Test
    void save_shouldAllowZeroBudgetLimit() {
        DepartmentBudget budget = new DepartmentBudget();
        budget.setBudgetLimit(BigDecimal.ZERO);

        Department dept = new Department();
        dept.setName("IT");

        when(repository.save(any())).thenAnswer(inv -> {
            DepartmentBudget b = inv.getArgument(0);
            b.setId(1L);
            b.setDepartment(dept);
            return b;
        });

        DepartmentBudget saved = service.save(budget);

        assertThat(saved.getId()).isEqualTo(1L);
        verify(repository).save(budget);
    }

    @Test
    void save_shouldCreateNewBudgetWithAuditLog() {
        DepartmentBudget budget = new DepartmentBudget();
        budget.setBudgetLimit(new BigDecimal("5000.00"));

        Department dept = new Department();
        dept.setName("Muhasebe");

        when(repository.save(any())).thenAnswer(inv -> {
            DepartmentBudget b = inv.getArgument(0);
            b.setId(2L);
            b.setDepartment(dept);
            return b;
        });

        DepartmentBudget saved = service.save(budget);

        assertThat(saved.getId()).isEqualTo(2L);
        verify(auditLogService).log(any(), eq("Bütçe"), any(), contains("Yeni bütçe oluşturuldu"));
    }

    @Test
    void save_shouldUpdateExistingBudgetWithAuditLog() {
        DepartmentBudget budget = new DepartmentBudget();
        budget.setId(3L);
        budget.setBudgetLimit(new BigDecimal("8000.00"));

        Department dept = new Department();
        dept.setName("Pazarlama");

        when(repository.save(any())).thenAnswer(inv -> {
            DepartmentBudget b = inv.getArgument(0);
            b.setDepartment(dept);
            return b;
        });

        service.save(budget);

        verify(auditLogService).log(any(), eq("Bütçe"), any(), contains("Bütçe güncellendi"));
    }

    @Test
    void delete_shouldDeleteWithAuditLog() {
        DepartmentBudget budget = new DepartmentBudget();
        budget.setId(1L);

        Department dept = new Department();
        dept.setName("Satış");
        budget.setDepartment(dept);

        when(repository.findById(1L)).thenReturn(Optional.of(budget));

        service.delete(1L);

        verify(repository).deleteById(1L);
        verify(auditLogService).log(any(), eq("Bütçe"), eq(1L), contains("Bütçe silindi"));
    }

    @Test
    void findByYearAndMonth_shouldDelegateToRepository() {
        DepartmentBudget b = new DepartmentBudget();
        when(repository.findByBudgetYearAndBudgetMonth(2026, 6)).thenReturn(List.of(b));

        List<DepartmentBudget> result = service.findByYearAndMonth(2026, 6);

        assertThat(result).hasSize(1);
        verify(repository).findByBudgetYearAndBudgetMonth(2026, 6);
    }

    @Test
    void findByDepartmentAndYearAndMonth_shouldReturnEmpty_whenNotFound() {
        when(repository.findByDepartmentIdAndBudgetYearAndBudgetMonth(99L, 2026, 1))
                .thenReturn(Optional.empty());

        Optional<DepartmentBudget> result = service.findByDepartmentAndYearAndMonth(99L, 2026, 1);

        assertThat(result).isEmpty();
    }
}
