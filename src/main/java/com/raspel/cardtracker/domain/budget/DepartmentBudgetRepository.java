package com.raspel.cardtracker.domain.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface DepartmentBudgetRepository extends JpaRepository<DepartmentBudget, Long> {
    @Query("SELECT db FROM DepartmentBudget db JOIN FETCH db.department WHERE db.department.id = :deptId AND db.budgetYear = :year AND db.budgetMonth = :month")
    Optional<DepartmentBudget> findByDepartmentIdAndBudgetYearAndBudgetMonth(@Param("deptId") Long departmentId, @Param("year") Integer year, @Param("month") Integer month);

    @Query("SELECT db FROM DepartmentBudget db JOIN FETCH db.department WHERE db.budgetYear = :year AND db.budgetMonth = :month")
    List<DepartmentBudget> findByBudgetYearAndBudgetMonth(@Param("year") Integer year, @Param("month") Integer month);

    @Query("SELECT db FROM DepartmentBudget db JOIN FETCH db.department ORDER BY db.budgetYear DESC, db.budgetMonth DESC")
    List<DepartmentBudget> findAll();

    @Query("SELECT db FROM DepartmentBudget db JOIN FETCH db.department WHERE db.department.id = :deptId ORDER BY db.budgetYear DESC, db.budgetMonth DESC")
    List<DepartmentBudget> findByDepartmentIdOrderByBudgetYearDescBudgetMonthDesc(@Param("deptId") Long departmentId);
}
