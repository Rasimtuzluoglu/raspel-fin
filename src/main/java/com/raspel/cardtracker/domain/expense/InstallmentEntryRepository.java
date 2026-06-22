package com.raspel.cardtracker.domain.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InstallmentEntryRepository extends JpaRepository<InstallmentEntry, Long> {

    @Query("SELECT ie FROM InstallmentEntry ie JOIN FETCH ie.expense e JOIN FETCH e.card LEFT JOIN FETCH e.contact WHERE ie.dueYear = :dueYear AND ie.dueMonth = :dueMonth")
    List<InstallmentEntry> findByDueYearAndDueMonth(@Param("dueYear") Integer dueYear, @Param("dueMonth") Integer dueMonth);

    @Query("SELECT ie FROM InstallmentEntry ie JOIN FETCH ie.expense WHERE ie.expense.id = :expenseId")
    List<InstallmentEntry> findByExpenseId(@Param("expenseId") Long expenseId);

    @Modifying
    @Query("DELETE FROM InstallmentEntry ie WHERE ie.expense.id = :expenseId")
    void deleteByExpenseId(@Param("expenseId") Long expenseId);

    @Query("SELECT ie FROM InstallmentEntry ie JOIN FETCH ie.expense e JOIN FETCH e.card LEFT JOIN FETCH e.contact WHERE ie.dueYear = :year AND ie.dueMonth = :month")
    List<InstallmentEntry> findByYearAndMonthWithDetails(@Param("year") Integer year, @Param("month") Integer month);

    @Query("SELECT ie FROM InstallmentEntry ie JOIN FETCH ie.expense e JOIN FETCH e.card LEFT JOIN FETCH e.contact WHERE ie.dueYear = :year AND ie.dueMonth = :month AND e.card.id = :cardId")
    List<InstallmentEntry> findByYearAndMonthAndCardId(@Param("year") Integer year, @Param("month") Integer month, @Param("cardId") Long cardId);

    @Query("SELECT COALESCE(SUM(ie.amount), 0) FROM InstallmentEntry ie JOIN ie.expense e WHERE ie.dueYear = :year AND ie.dueMonth = :month")
    BigDecimal sumAmountByYearAndMonth(@Param("year") Integer year, @Param("month") Integer month);

    @Query("SELECT COALESCE(SUM(ie.amount), 0) FROM InstallmentEntry ie JOIN ie.expense e WHERE ie.dueYear = :year AND ie.dueMonth = :month AND e.card.id = :cardId")
    BigDecimal sumAmountByYearAndMonthAndCardId(@Param("year") Integer year, @Param("month") Integer month, @Param("cardId") Long cardId);

    @Query("SELECT e.card.name, e.card.department.name, SUM(ie.amount) FROM InstallmentEntry ie JOIN ie.expense e " +
           "WHERE ie.dueYear = :year AND ie.dueMonth = :month " +
           "GROUP BY e.card.name, e.card.department.name")
    List<Object[]> findTotalsGroupedByCardAndDept(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COALESCE(SUM(ie.amount), 0) FROM InstallmentEntry ie JOIN ie.expense e WHERE e.card.id = :cardId AND ie.isPaid = false")
    BigDecimal sumUnpaidAmountByCardId(@Param("cardId") Long cardId);

    @Query("SELECT e.card.id, COALESCE(SUM(ie.amount), 0) FROM InstallmentEntry ie JOIN ie.expense e WHERE ie.isPaid = false GROUP BY e.card.id")
    List<Object[]> sumUnpaidAmountGroupedByCardId();

    @Query("SELECT COALESCE(SUM(ie.amount), 0) FROM InstallmentEntry ie WHERE ie.dueYear = :year AND ie.dueMonth = :month AND ie.isPaid = true")
    BigDecimal sumPaidAmountByYearAndMonth(@Param("year") Integer year, @Param("month") Integer month);

    @Query("SELECT COALESCE(SUM(ie.amount), 0) FROM InstallmentEntry ie WHERE ie.dueYear = :year AND ie.dueMonth = :month AND ie.isPaid = false")
    BigDecimal sumUnpaidAmountByYearAndMonth(@Param("year") Integer year, @Param("month") Integer month);

    @Query("SELECT ie FROM InstallmentEntry ie JOIN FETCH ie.expense e JOIN FETCH e.card LEFT JOIN FETCH e.contact WHERE ie.isPaid = false AND e.card.active = true")
    List<InstallmentEntry> findAllUnpaidWithDetails();

    @Query(value = "SELECT ie FROM InstallmentEntry ie JOIN FETCH ie.expense e JOIN FETCH e.card LEFT JOIN FETCH e.contact WHERE ie.dueYear = :year AND ie.dueMonth = :month " +
           "AND (:cardId IS NULL OR e.card.id = :cardId) " +
           "AND (:term IS NULL OR :term = '' OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.category) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.name) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.department.name) LIKE LOWER(CONCAT('%', :term, '%')))",
           countQuery = "SELECT COUNT(ie) FROM InstallmentEntry ie JOIN ie.expense e WHERE ie.dueYear = :year AND ie.dueMonth = :month " +
           "AND (:cardId IS NULL OR e.card.id = :cardId) " +
           "AND (:term IS NULL OR :term = '' OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.category) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.name) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.department.name) LIKE LOWER(CONCAT('%', :term, '%')))")
    Page<InstallmentEntry> findBySearchTermAndYearAndMonth(
             @Param("term") String term, @Param("year") Integer year, @Param("month") Integer month, @Param("cardId") Long cardId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(ie.amount), 0) FROM InstallmentEntry ie JOIN ie.expense e WHERE ie.dueYear = :year AND ie.dueMonth = :month " +
           "AND (:cardId IS NULL OR e.card.id = :cardId) " +
           "AND (:term IS NULL OR :term = '' OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.category) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.name) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.department.name) LIKE LOWER(CONCAT('%', :term, '%')))")
    BigDecimal sumAmountBySearchTermAndYearAndMonth(@Param("term") String term, @Param("year") Integer year, @Param("month") Integer month, @Param("cardId") Long cardId);

    @Query("SELECT ie FROM InstallmentEntry ie JOIN FETCH ie.expense e JOIN FETCH e.card LEFT JOIN FETCH e.contact WHERE " +
           "((ie.dueYear > :startYear) OR (ie.dueYear = :startYear AND ie.dueMonth >= :startMonth)) AND " +
           "((ie.dueYear < :endYear) OR (ie.dueYear = :endYear AND ie.dueMonth <= :endMonth)) AND " +
           "(:cardId IS NULL OR e.card.id = :cardId) AND " +
           "(:term IS NULL OR :term = '' OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.category) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.name) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.department.name) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "ORDER BY ie.dueYear, ie.dueMonth")
    List<InstallmentEntry> findByYearMonthRange(@Param("startYear") Integer startYear, @Param("startMonth") Integer startMonth,
            @Param("endYear") Integer endYear, @Param("endMonth") Integer endMonth,
            @Param("cardId") Long cardId, @Param("term") String term);

    @Query("SELECT COALESCE(SUM(ie.amount), 0) FROM InstallmentEntry ie JOIN ie.expense e WHERE " +
           "((ie.dueYear > :startYear) OR (ie.dueYear = :startYear AND ie.dueMonth >= :startMonth)) AND " +
           "((ie.dueYear < :endYear) OR (ie.dueYear = :endYear AND ie.dueMonth <= :endMonth)) AND " +
           "(:cardId IS NULL OR e.card.id = :cardId) AND " +
           "(:term IS NULL OR :term = '' OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.category) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.name) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(e.card.department.name) LIKE LOWER(CONCAT('%', :term, '%')))")
    BigDecimal sumAmountByYearMonthRange(@Param("startYear") Integer startYear, @Param("startMonth") Integer startMonth,
            @Param("endYear") Integer endYear, @Param("endMonth") Integer endMonth,
            @Param("cardId") Long cardId, @Param("term") String term);
}
