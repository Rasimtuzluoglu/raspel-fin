package com.raspel.cardtracker.domain.budget;

import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentBudgetService {

    private final DepartmentBudgetRepository repository;
    private final AuditLogService auditLogService;

    public List<DepartmentBudget> findAll() {
        return repository.findAll();
    }

    public List<DepartmentBudget> findByYearAndMonth(Integer year, Integer month) {
        return repository.findByBudgetYearAndBudgetMonth(year, month);
    }

    public Optional<DepartmentBudget> findByDepartmentAndYearAndMonth(Long departmentId, Integer year, Integer month) {
        return repository.findByDepartmentIdAndBudgetYearAndBudgetMonth(departmentId, year, month);
    }

    public DepartmentBudget save(DepartmentBudget budget) {
        boolean isNew = budget.getId() == null;
        if (budget.getBudgetLimit() == null || budget.getBudgetLimit().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Bütçe limiti negatif olamaz");
        }
        DepartmentBudget saved = repository.save(budget);
        String deptName = saved.getDepartment() != null ? saved.getDepartment().getName() : "Bilinmeyen Departman";
        auditLogService.log(
                isNew ? AuditAction.CREATE : AuditAction.UPDATE,
                "Bütçe",
                saved.getId(),
                (isNew ? "Yeni bütçe oluşturuldu: " : "Bütçe güncellendi: ") + deptName
        );
        return saved;
    }

    public void delete(Long id) {
        repository.findById(id).ifPresent(budget -> {
            String deptName = budget.getDepartment() != null ? budget.getDepartment().getName() : "Bilinmeyen Departman";
            auditLogService.log(AuditAction.DELETE, "Bütçe", id, "Bütçe silindi: " + deptName);
        });
        repository.deleteById(id);
    }
}
