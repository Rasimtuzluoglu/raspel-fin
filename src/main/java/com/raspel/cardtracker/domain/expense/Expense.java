package com.raspel.cardtracker.domain.expense;

import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.contact.Contact;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expense")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Version
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(nullable = false)
    @NotBlank(message = "Açıklama zorunludur")
    private String description;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    @Positive(message = "Tutar pozitif olmalıdır")
    private BigDecimal totalAmount;

    @Column(nullable = false)
    @Builder.Default
    @Min(value = 1, message = "En az 1 taksit")
    @Max(value = 48, message = "En fazla 48 taksit")
    private Integer installments = 1;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(length = 50)
    private String category;

    @Column(length = 20)
    private String tag;

    @Column(length = 3, nullable = false)
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "original_amount", precision = 15, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "exchange_rate", precision = 15, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "receipt_path")
    private String receiptPath;

    @Column(name = "receipt_content_type")
    private String receiptContentType;

    @Column(name = "created_by", length = 50)
    @CreatedBy
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_by", length = 50)
    @LastModifiedBy
    private String updatedBy;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "expense", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InstallmentEntry> installmentEntries = new ArrayList<>();
}
