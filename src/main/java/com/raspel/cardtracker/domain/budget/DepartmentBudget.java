package com.raspel.cardtracker.domain.budget;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.raspel.cardtracker.domain.department.Department;

import lombok.EqualsAndHashCode;

@Entity
@Table(name = "department_budget", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"department_id", "budget_year", "budget_month"})
})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DepartmentBudget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Version
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "budget_year", nullable = false)
    private Integer budgetYear;

    @Column(name = "budget_month", nullable = false)
    private Integer budgetMonth;

    @Column(name = "budget_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal budgetLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
