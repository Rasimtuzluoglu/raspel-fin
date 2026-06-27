package com.raspel.cardtracker.domain.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    @Query("SELECT b FROM Budget b JOIN FETCH b.department WHERE b.department.id = :deptId AND b.year = :year AND b.month = :month")
    Optional<Budget> findByDepartmentIdAndYearAndMonth(@Param("deptId") Long deptId, @Param("year") Integer year, @Param("month") Integer month);

    @Query("SELECT b FROM Budget b JOIN FETCH b.department WHERE b.year = :year AND b.month = :month")
    List<Budget> findByYearAndMonth(@Param("year") Integer year, @Param("month") Integer month);

    @Query("SELECT b FROM Budget b JOIN FETCH b.department WHERE b.department.id = :deptId ORDER BY b.year DESC, b.month DESC")
    List<Budget> findByDepartmentIdOrderByYearDescMonthDesc(@Param("deptId") Long deptId);
}
