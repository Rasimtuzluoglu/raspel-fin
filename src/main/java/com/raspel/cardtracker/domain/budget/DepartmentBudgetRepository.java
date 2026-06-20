package com.raspel.cardtracker.domain.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface DepartmentBudgetRepository extends JpaRepository<DepartmentBudget, Long> {
    Optional<DepartmentBudget> findByDepartmentIdAndBudgetYearAndBudgetMonth(Long departmentId, Integer year, Integer month);

    @Query("SELECT db FROM DepartmentBudget db JOIN FETCH db.department WHERE db.budgetYear = :year AND db.budgetMonth = :month")
    List<DepartmentBudget> findByBudgetYearAndBudgetMonth(@Param("year") Integer year, @Param("month") Integer month);
    List<DepartmentBudget> findByDepartmentIdOrderByBudgetYearDescBudgetMonthDesc(Long departmentId);
}
