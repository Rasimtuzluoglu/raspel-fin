package com.raspel.cardtracker.domain.budget;

import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<Budget> findAll() {
        return budgetRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Budget> findByYearAndMonth(Integer year, Integer month) {
        return budgetRepository.findByYearAndMonth(year, month);
    }

    @Transactional(readOnly = true)
    public Optional<Budget> findByDepartmentAndYearAndMonth(Long deptId, Integer year, Integer month) {
        return budgetRepository.findByDepartmentIdAndYearAndMonth(deptId, year, month);
    }

    public Budget save(Budget budget) {
        if (budget.getLimitAmount() != null && budget.getLimitAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Limit tutarı 0'dan küçük olamaz");
        }
        boolean isNew = budget.getId() == null;
        Budget saved = budgetRepository.save(budget);
        auditLogService.log(isNew ? AuditAction.CREATE : AuditAction.UPDATE, "Bütçe", saved.getId(),
                "Bütçe " + (isNew ? "oluşturuldu" : "güncellendi") + ": Departman="
                        + (saved.getDepartment() != null ? saved.getDepartment().getName() : "-")
                        + " " + saved.getYear() + "/" + saved.getMonth()
                        + " Limit=" + saved.getLimitAmount());
        return saved;
    }

    public void delete(Long id) {
        budgetRepository.findById(id).ifPresent(budget -> {
            auditLogService.log(AuditAction.DELETE, "Bütçe", id,
                    "Bütçe silindi: Departman="
                            + (budget.getDepartment() != null ? budget.getDepartment().getName() : "-")
                            + " " + budget.getYear() + "/" + budget.getMonth());
        });
        budgetRepository.deleteById(id);
    }
}
