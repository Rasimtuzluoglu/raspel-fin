package com.raspel.cardtracker.domain.card;

import com.raspel.cardtracker.domain.department.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    @Query("SELECT c FROM Card c LEFT JOIN FETCH c.department WHERE c.active = true")
    List<Card> findAllByActiveTrue();
    @Query("SELECT c FROM Card c LEFT JOIN FETCH c.department WHERE c.category = :category")
    List<Card> findByCategory(@Param("category") String category);
    @Query("SELECT c FROM Card c LEFT JOIN FETCH c.department WHERE c.bank = :bank")
    List<Card> findByBank(@Param("bank") String bank);
    @Query("SELECT c FROM Card c LEFT JOIN FETCH c.department WHERE c.department = :department")
    List<Card> findByDepartment(@Param("department") Department department);
}
