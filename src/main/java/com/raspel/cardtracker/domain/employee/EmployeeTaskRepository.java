package com.raspel.cardtracker.domain.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EmployeeTaskRepository extends JpaRepository<EmployeeTask, Long> {

    @Query("SELECT t FROM EmployeeTask t LEFT JOIN FETCH t.assignedTo ORDER BY t.dueDate ASC")
    List<EmployeeTask> findAllWithEmployee();
}
