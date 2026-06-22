package com.raspel.cardtracker.domain.cheque;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
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
}
