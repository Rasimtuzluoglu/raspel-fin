package com.raspel.cardtracker.domain.expense;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "installment_entry")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Version
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @Column(name = "due_year", nullable = false)
    @Min(2000)
    @Max(2100)
    private Integer dueYear;

    @Column(name = "due_month", nullable = false)
    @Min(1)
    @Max(12)
    private Integer dueMonth;

    @Column(nullable = false, precision = 15, scale = 2)
    @Positive(message = "Taksit tutarı pozitif olmalıdır")
    private BigDecimal amount;

    @Column(name = "is_paid", nullable = false)
    @Builder.Default
    private Boolean isPaid = false;
}
