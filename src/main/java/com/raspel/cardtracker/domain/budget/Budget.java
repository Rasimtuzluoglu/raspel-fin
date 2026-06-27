package com.raspel.cardtracker.domain.budget;

import com.raspel.cardtracker.domain.department.Department;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "budget")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "\"year\"", nullable = false)
    private Integer year;

    @Column(name = "\"month\"", nullable = false)
    private Integer month;

    @Column(name = "limit_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal limitAmount = BigDecimal.ZERO;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
