package com.raspel.cardtracker.domain.card;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.raspel.cardtracker.domain.department.Department;

@Entity
@Table(name = "card")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Version
    private Integer version;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String bank;

    @Column(name = "card_limit", nullable = false)
    @Builder.Default
    private BigDecimal cardLimit = BigDecimal.ZERO;

    @Column(length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", length = 30)
    @Builder.Default
    private CardType cardType = CardType.CREDIT_CARD;

    @Column(name = "monthly_assignment")
    private BigDecimal monthlyAssignment;

    @Column(length = 7)
    @Builder.Default
    private String color = "#1976D2";

    @Min(1)
    @Max(31)
    @Column(name = "closing_day", nullable = false)
    @Builder.Default
    private Integer closingDay = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "holder_name", length = 100)
    private String holderName;

    @Min(1)
    @Max(31)
    @Column(name = "due_day", nullable = false)
    @Builder.Default
    private Integer dueDay = 10;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
