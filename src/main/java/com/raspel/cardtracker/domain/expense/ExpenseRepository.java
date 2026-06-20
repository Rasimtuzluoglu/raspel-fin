package com.raspel.cardtracker.domain.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("SELECT e FROM Expense e LEFT JOIN FETCH e.card LEFT JOIN FETCH e.contact WHERE e.card.id = :cardId")
    List<Expense> findByCardId(@Param("cardId") Long cardId);

    @Query("SELECT e FROM Expense e LEFT JOIN FETCH e.card LEFT JOIN FETCH e.contact WHERE e.expenseDate BETWEEN :startDate AND :endDate")
    List<Expense> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT e FROM Expense e JOIN FETCH e.card WHERE e.card.id = :cardId AND e.expenseDate BETWEEN :startDate AND :endDate")
    List<Expense> findByCardIdAndDateRange(@Param("cardId") Long cardId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT e FROM Expense e JOIN FETCH e.card LEFT JOIN FETCH e.contact")
    List<Expense> findAllWithCard();

    @Modifying
    @Query("DELETE FROM Expense e WHERE e.card.id = :cardId")
    void deleteAllByCardId(@Param("cardId") Long cardId);

    @Query(value = "SELECT e FROM Expense e JOIN FETCH e.card WHERE " +
           "(:term IS NULL OR :term = '' OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.category) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.name) LIKE LOWER(CONCAT('%', :term, '%')))",
           countQuery = "SELECT COUNT(e) FROM Expense e WHERE " +
           "(:term IS NULL OR :term = '' OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.category) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.name) LIKE LOWER(CONCAT('%', :term, '%')))")
    Page<Expense> findBySearchTerm(@Param("term") String term, Pageable pageable);

    @Query("SELECT e.category, COUNT(e) FROM Expense e WHERE LOWER(e.description) LIKE LOWER(CONCAT('%', :desc, '%')) AND e.category IS NOT NULL AND e.category <> '' GROUP BY e.category ORDER BY COUNT(e) DESC")
    List<Object[]> findCategoryByDescriptionKeyword(@Param("desc") String desc);
}
