package com.raspel.cardtracker.domain.cheque;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChequeRepository extends JpaRepository<Cheque, Long> {
    @Query("SELECT c FROM Cheque c LEFT JOIN FETCH c.contact ORDER BY c.maturityDate ASC")
    List<Cheque> findAllByOrderByMaturityDateAsc();

    @Query("SELECT c FROM Cheque c LEFT JOIN FETCH c.contact WHERE " +
           "(:term IS NULL OR :term = '' OR LOWER(c.chequeNumber) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.bank) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.party) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "ORDER BY c.maturityDate ASC")
    List<Cheque> searchByTerm(@Param("term") String term);

    @Query("SELECT c FROM Cheque c LEFT JOIN FETCH c.contact WHERE c.chequeNumber = :chequeNumber")
    Optional<Cheque> findByChequeNumber(@Param("chequeNumber") String chequeNumber);

    @Query(value = "SELECT c FROM Cheque c LEFT JOIN FETCH c.contact WHERE " +
           "(:term IS NULL OR :term = '' OR LOWER(c.chequeNumber) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.bank) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.party) LIKE LOWER(CONCAT('%', :term, '%'))) AND " +
           "(:type IS NULL OR c.type = :type) AND " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:startDate IS NULL OR c.maturityDate >= :startDate) AND " +
           "(:endDate IS NULL OR c.maturityDate <= :endDate) " +
           "ORDER BY c.maturityDate ASC",
           countQuery = "SELECT COUNT(c) FROM Cheque c WHERE " +
           "(:term IS NULL OR :term = '' OR LOWER(c.chequeNumber) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.bank) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.party) LIKE LOWER(CONCAT('%', :term, '%'))) AND " +
           "(:type IS NULL OR c.type = :type) AND " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:startDate IS NULL OR c.maturityDate >= :startDate) AND " +
           "(:endDate IS NULL OR c.maturityDate <= :endDate)")
    Page<Cheque> findFilteredPaged(
            @Param("term") String term,
            @Param("type") ChequeType type,
            @Param("status") ChequeStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM Cheque c WHERE c.type = :type AND c.status = :status")
    java.math.BigDecimal sumAmountByTypeAndStatus(@Param("type") ChequeType type, @Param("status") ChequeStatus status);
}
